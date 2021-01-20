package org.openstreetmap.osmosis.tagtransform.impl;

import org.openstreetmap.osmosis.tagtransform.Match;
import org.openstreetmap.osmosis.tagtransform.Matcher;
import org.openstreetmap.osmosis.tagtransform.TTEntityType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

public class WayTagMatcher implements Matcher {

    private Long id;
    private long matchHits;

    WayTagMatcher(Long id) {
        this.id = id;
    }

    @Override
    public Collection<Match> match(Long id, Map<String, String> tags, TTEntityType type, String uname, int uid) {

        if (!this.id.equals(id))  {
            return null;
        }
        matchHits += 1;

        return Collections.singleton(NULL_MATCH);
    }

    @Override
    public void outputStats(StringBuilder output, String indent) {
        output.append(indent);
        output.append("Id[");
        output.append(id);
        output.append("]: ");
        output.append(matchHits);
        output.append('\n');
    }

    private static final Match NULL_MATCH = new Match() {
        @Override
        public int getValueGroupCount() {
            return 0;
        }


        @Override
        public String getValue(int group) {
            return null;
        }


        @Override
        public String getMatchID() {
            return null;
        }


        @Override
        public int getKeyGroupCount() {
            return 0;
        }


        @Override
        public String getKey(int group) {
            return null;
        }
    };
}
