/*
 * Copyright (C) 2012 - 2013 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.oreland.teambuilder.sync;

import java.util.Properties;

public interface Synchronizer {

    public enum RequestMethod {GET, POST, PATCH, PUT;}

    enum AuthMethod {
        OAUTH2, USER_PASS

    }

    enum Status {
        OK, CANCEL, ERROR, INCORRECT_USAGE, SKIP, NEED_AUTH, NEED_REFRESH;
        public Exception ex = null;
        public AuthMethod authMethod = null;
    }

    /**
     * @return name of this synchronizer
     */
    public String getName();

    /**
     * read config
     *
     * @param config
     */
    public void init(Properties config);

    public void reset();

    /**
     * Connect
     *
     * @return true ok false cancel/fail
     */
    public Status connect();

    /**
     * logout
     *
     * @return
     */
    public void logout();
}
