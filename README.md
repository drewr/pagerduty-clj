# Clojure <> PagerDuty

Very much a simple start as I was trying to navigate the incidents and
schedules for my organization and didn't find any prior art here.

Maybe it's useful to you.  PRs welcome.  Generating code from the
[openapi spec](https://github.com/PagerDuty/api-schema) did not work
for me, so that would be a helpful addition.

## Usage

1. Create an API token in the "User Settings" tab in your profile.
2. Plug that into the `tok` argument to the primary functions.

## License

Copyright © 2024 Drew Raines

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
