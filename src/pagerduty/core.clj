(ns pagerduty.core
  (:require [cheshire.core :as json]
            [clj-http.client :as http]
            [clojure.data.csv :as csv]
            [flatland.ordered.map :refer [ordered-map]]
            [java-time.api :as time]))

(defn url-base []
  "https://api.pagerduty.com")

(defn select-keys* [m paths]
  (into {} (map (fn [p]
                  (if (fn? (peek p))
                    (let [f (peek p)
                          all-but-the-fn (-> p reverse rest reverse)]
                      [(peek all-but-the-fn) (f (get-in m all-but-the-fn))])
                    [(peek p) (get-in m p)])))
        paths))

(def pd
  (memoize
   (fn
     ([tok uri]
      (pd tok uri ""))
     ([tok uri query]
      (-> (http/get
           (format "%s%s" (url-base) uri)
           {:headers {:authorization (format "Token token=%s " tok)
                      :accept "application/json"
                      :content-type "application/json"}
            :query-params {:total true
                           :limit 100
                           :query query}})
          :body
          (json/decode true))))))

(defn utc-time [epoch]
  (-> epoch
      time/instant
      (time/zoned-date-time "UTC")))

(defn local-time [epoch]
  (-> (utc-time epoch)
      (time/zoned-date-time (.getZone (time/system-clock)))))

(defn excel-time [epoch]
  (time/format "yyyy-MM-dd HH:mm:ss" (utc-time epoch)))

(defn incidents
  ([tok]
   (pd tok "/incidents")))

(defn teams
  ([tok]
   (teams tok ""))
  ([tok query]
   (let [ts (-> (pd tok "/teams" query) :teams)]
     (cond
       (> (count ts) 0)
       ts
       :else
       (let [ts-all (-> (pd tok "/teams" "") :teams)]
         (->> ts-all
              (filter
               #(re-find
                 (re-pattern (format "(?i)%s" query)) (:id %)))
              #_(map #(select-keys % [:id :name]))))))))

(defn team-members
  ([tok query]
   (let [ids (teams tok query)]
     (when ids
       (cond
         (> (count ids) 1)
         {:count (count ids)
          :teams (map #(select-keys % [:id :name]) ids)}
         (> (count ids) 0)
         (let [id (-> ids first :id)]
           (->> (pd tok (format "/teams/%s/members" id))
                :members
                (map #(select-keys* % [[:user :id]
                                       [:user :summary]]))
                )))))))

(defn users
  ([tok]
   (users tok ""))
  ([tok query]
   (pd tok "/users" query)))

(defn user
  ([tok id]
   (pd tok (format "%s/users/%s" (url-base) id))))

(defn user-teams [tok query]
  (->> (users tok query) :users first :teams (map :summary)))

(defn schedules
  ([tok]
   (schedules tok ""))
  ([tok query]
   (pd tok "/schedules" query)))


;; Mostly taken from leontalbot at
;; https://stackoverflow.com/a/48244002, thanks!
(defn write-csv
  "Takes a file (path, name and extension) and
   csv-data (vector of vectors with all values) and
   writes csv file."
  [file csv-data]
  (with-open [writer (clojure.java.io/writer file :append false)]
    (csv/write-csv writer csv-data)))

(defn maps->csv-data
  "Takes a collection of maps and returns csv-data
   (vector of vectors with all values)."
  [maps]
  (let [columns (-> maps first keys)
        headers (mapv name columns)
        rows (mapv #(mapv % columns) maps)]
    (into [headers] rows)))

(defn write-csv-from-maps
  "Takes a file (path, name and extension) and a collection of maps
   transforms data (vector of vectors with all values)
   writes csv file."
  [file maps]
  (->> maps maps->csv-data (write-csv file)))

(defn creator-email [incident]
  (let [f #(= (:creator incident) (:_id %))]
    (-> (first (filter f (:team incident)))
        :profile
        :email)))

(defn enhance-incident [i]
  (assoc (ordered-map)
    :id (-> i :_id)
    :created-utc (-> i :created :$date excel-time str)
    :type (-> i :type)
    :severity (-> i :severity)
    :creator (creator-email i)
    :seconds-to-resolve (-> i :time_to_resolution)
    :hours-to-resolve (int (/ (-> i :time_to_resolution) 3600))
    :days-to-resolve (int (/ (-> i :time_to_resolution) 3600 24))
    :hours-of-customer-impact (float
                               (/ (-> i :duration_of_customer_impact) 3600))
    :seconds-of-customer-impact (-> i :duration_of_customer_impact)
    :description (-> i :description)))

(comment
  (write-csv-from-maps
   "/tmp/blameless.csv"
   (->> (incidents "packet" tok)
        (map
         #(select-keys* % [[:_id]
                           [:created :$date (comp str excel-time)]
                           [:type]
                           [:severity]
                           [:description]]))))

  (write-csv-from-maps
   "/tmp/blameless.csv"
   (->> (incidents "packet" tok)
        (map enhance-incident)))

  (->> (pd packet "/schedules")
       :schedules
       (map #(select-keys* % [[:id]
                              [:name]])))

  (->> (pd packet "/oncalls")
       :oncalls
       second)

  (->> (http/get
        (format "%s%s" (url-base) "/oncalls")
        {:headers {:authorization (format "Token token=%s " packet)
                   :accept "application/json"
                   :content-type "application/json"}
         :query-params {:total true
                        :limit 100
                        :query ""
                        :schedule_ids ["PLBW9ZW"]}
         :multi-param-style :array})
       :body
       (json/decode))

  (->> (incidents "packet" tok)
       (filter #(.startsWith (:severity %) "SEV1"))
       (filter #(= "general" (:type %)))
       (filter #(.startsWith (-> % :created :$date utc-time str) "2023-"))
       (map enhance-incident)
       (map #(println
              (format
               "%s (%s) %s (%.2f hrs)"
               (:created-utc %)
               (:id %)
               (:description %)
               (:hours-of-customer-impact %)))))

  (->> (incidents "packet" tok)
       (filter #(= 1952 (:_id %)))
       first
       :team
       (map #(get-in % [:profile :email]))
       sort
       (map println))
  )
