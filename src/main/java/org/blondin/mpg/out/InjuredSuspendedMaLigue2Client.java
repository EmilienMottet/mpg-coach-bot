package org.blondin.mpg.out;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.blondin.mpg.AbstractClient;
import org.blondin.mpg.config.Config;
import org.blondin.mpg.out.model.OutType;
import org.blondin.mpg.out.model.Player;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

public class InjuredSuspendedMaLigue2Client extends AbstractClient {

    private static final Map<String, String> TEAM_NAME_WRAPPER = new HashMap<>();

    static {
        /*
         * Team name "maligue2 -> MPG" wrapper
         */
        TEAM_NAME_WRAPPER.put("QRM", "Quevilly Rouen");
        TEAM_NAME_WRAPPER.put("AS Saint-Etienne", "St Etienne");
    }

    private List<Player> cache = null;

    private InjuredSuspendedMaLigue2Client(Config config) {
        super(config);
    }

    public static InjuredSuspendedMaLigue2Client build(Config config) {
        return build(config, null);
    }

    public static InjuredSuspendedMaLigue2Client build(Config config, String urlOverride) {
        InjuredSuspendedMaLigue2Client client = new InjuredSuspendedMaLigue2Client(config);
        client.setUrl(StringUtils.defaultString(urlOverride, "https://maligue2.fr/2020/08/20/joueurs-blesses-et-suspendus/"));
        return client;
    }

    public List<Player> getPlayers() {
        if (cache == null) {
            cache = getPlayers(getHtmlContent());
        }
        return cache;
    }

    static String getMpgTeamName(String name) {
        if (TEAM_NAME_WRAPPER.containsKey(name)) {
            return TEAM_NAME_WRAPPER.get(name);
        }
        return name;
    }

    public Player getPlayer(String playerName, String teamName) {
        // For composed lastName (highest priority than firstName composed), we replace space by '-'
        String lastName = playerName;
        if (!playerName.startsWith("De ")) {
            int spaceIndex = lastName.lastIndexOf(' ');
            if (spaceIndex > 0) {
                lastName = lastName.substring(0, spaceIndex);
            }
            lastName = lastName.replace(' ', '-');
            lastName = lastName.replace("Saint-", "St-");
        }
        for (Player player : getPlayers()) {
            if (lastName.equalsIgnoreCase(player.getFullNameWithPosition())) {
                if (StringUtils.isNotBlank(teamName) && !player.getTeam().contains(teamName)) {
                    continue;
                }
                return player;
            }
        }
        return null;
    }

    protected String getHtmlContent() {
        return get("", String.class, TIME_HOUR_IN_MILLI_SECOND);
    }

    protected List<Player> getPlayers(String content) {
        List<Player> players = new ArrayList<>();
        Document doc = Jsoup.parse(content);
        for (Element item : doc.select("tr")) {
            if (item.selectFirst("th.column-1") != null && "Club".equals(item.selectFirst("th.column-1").text())) {
                continue;
            }
            String team = item.selectFirst("td.column-1").text();
            players.addAll(parsePlayers(team, item.selectFirst("td.column-2"), OutType.SUSPENDED));
            players.addAll(parsePlayers(team, item.selectFirst("td.column-3"), OutType.INJURY_RED));
            players.addAll(parsePlayers(team, item.selectFirst("td.column-4"), OutType.ASBENT));
        }
        return players;
    }

    private List<Player> parsePlayers(String team, Element e, OutType outType) {
        List<Player> players = new ArrayList<>();
        for (Node node : e.childNodes()) {
            if (node instanceof TextNode) {
                String content = ((TextNode) node).getWholeText();
                if (StringUtils.isBlank(content)) {
                    continue;
                }
                Player player = new Player();
                player.setTeam(getMpgTeamName(team));
                player.setOutType(outType);
                player.setLength("");
                player.setDescription("");
                int lBegin = content.lastIndexOf('(');
                if (lBegin > 0) {
                    player.setFullNameWithPosition(content.substring(0, lBegin).trim());
                    int lEnd = content.lastIndexOf(')');
                    // If no parentheses ending, length information is until end (because no parenthesis)
                    if (lEnd <= 0) {
                        lEnd = content.length();
                    }
                    player.setLength(content.substring(lBegin + 1, lEnd));
                } else {
                    player.setFullNameWithPosition(content.trim());
                }
                players.add(player);
            }
        }
        return players;
    }

}
