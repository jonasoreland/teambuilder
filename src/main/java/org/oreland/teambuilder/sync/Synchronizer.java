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

import org.oreland.teambuilder.Context;
import org.oreland.teambuilder.Pair;

import java.io.IOException;
import java.util.List;
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

    class Specifier {
        public String name;
        public String key;

        public Specifier(String name, String key) {
            this.name = name;
            this.key = key;
        }

        public Specifier(String s) {
            this.name = s;
            this.key = s;
        }

        public boolean isValid() {
            return name != null && key != null;
        }
    };

    public List<Specifier> listSections(Context ctx) throws Exception;
    public List<Specifier> listTeams(Context ctx) throws Exception;
    public List<Specifier> listPeriods(Context ctx) throws Exception;

    public void setSection(Context ctx, Specifier section) throws Exception;
    public Specifier getCurrentSection(Context ctx);

    public void setTeam(Context ctx, Specifier team) throws Exception;
    public Specifier getCurrentTeam(Context ctx);

    public void setPeriod(Context ctx, Specifier period) throws Exception;
    public Specifier getCurrentPeriod(Context ctx);
}
