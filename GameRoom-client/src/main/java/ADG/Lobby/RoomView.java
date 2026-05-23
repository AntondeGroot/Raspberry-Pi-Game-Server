package ADG.Lobby;

import ADG.Utils.Cookie;
import ADG.Utils.LanguageSelectorWidget;
import ADG.i18n.I18n;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.HeadingElement;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.*;

import java.util.List;
import java.util.Map;

public class RoomView extends Composite {

    private boolean isAdmin = false;

    interface RoomUiBinder extends UiBinder<Widget, RoomView> {}
    private static RoomUiBinder uiBinder = GWT.create(RoomUiBinder.class);

    @UiField
    VerticalPanel roomPanel;

    @UiField
    HeadingElement roomTitle;

    @UiField
    Button leaveRoomButton;

    @UiField
    Button deleteRoomButton;

    @UiField
    Button startGameButton;

    @UiField
    HTMLPanel langSelectorRow;

    @UiField
    HTMLPanel playerPanel;

    @UiField
    Button sendMessageButton;

    @UiField
    Label startInfoLabel;

    @UiField
    TextArea messageDisplayField;

    @UiField
    TextBox messageInputField;

    public RoomView() {
        initWidget(uiBinder.createAndBindUi(this));
        startGameButton.setText(I18n.c().startGame());
        leaveRoomButton.setText(I18n.c().leaveRoom());
        deleteRoomButton.setText(I18n.c().deleteRoom());
        sendMessageButton.setText(I18n.c().send());
        langSelectorRow.add(new LanguageSelectorWidget());
    }

    public Button getLeaveRoomButton() { return leaveRoomButton; }
    public Button getDeleteRoomButton() { return deleteRoomButton; }
    public Button getStartGameButton() { return startGameButton; }
    public HTMLPanel getPlayerPanel() { return playerPanel; }
    public TextArea getMessageDisplayField() { return messageDisplayField; }
    public TextBox getMessageInputField() { return messageInputField; }
    public Button getSendMessageButton() { return sendMessageButton; }

    public void refreshPlayerList(Map<String, String> userNames, Map<String, String> userProfiles) {
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        for (String userId : userNames.keySet()) {
            if (!first) json.append(",");
            first = false;
            String name = userNames.getOrDefault(userId, "?");
            String profileStr = userProfiles.get(userId);
            int globalIndex = 0;
            if (profileStr != null) {
                try { globalIndex = Integer.parseInt(profileStr); } catch (NumberFormatException ignored) {}
            }
            SpriteSheets.Sheet sheet = SpriteSheets.sheetFor(globalIndex);
            int localIndex = SpriteSheets.localIndexFor(globalIndex);
            int localCol = localIndex % sheet.cols;
            int localRow = localIndex / sheet.cols;
            String safeId = userId.replaceAll("[^a-zA-Z0-9]", "_");
            String safeName = name.replace("\\", "\\\\").replace("\"", "\\\"");
            // srcX/Y/W/H are the inset-adjusted source rect within the sprite sheet.
            // renderedImgW/H are the dimensions the full sheet image must be rendered at
            // so that one cell fills exactly displaySize (= R*2) pixels in D3.
            // renderedImgW = imgW * displaySize / srcW  →  passed as multiplier factors.
            json.append("{")
                .append("\"name\":\"").append(safeName).append("\"")
                .append(",\"safeId\":\"").append(safeId).append("\"")
                .append(",\"sheetUrl\":\"").append(sheet.url).append("\"")
                .append(",\"srcX\":").append(sheet.srcX(localCol))
                .append(",\"srcY\":").append(sheet.srcY(localRow))
                .append(",\"srcW\":").append(sheet.srcW())
                .append(",\"srcH\":").append(sheet.srcH())
                .append(",\"imgW\":").append(sheet.imgW)
                .append(",\"imgH\":").append(sheet.imgH)
                .append("}");
        }
        json.append("]");
        renderD3Simulation(playerPanel.getElement(), json.toString());
    }

    private native void renderD3Simulation(Element container, String playersJson) /*-{
        var d3 = $wnd.d3;
        if (!d3) return;

        if (container.__sim) { container.__sim.stop(); container.__sim = null; }
        var gen = (container.__gen || 0) + 1;
        container.__gen = gen;

        var players = JSON.parse(playersJson);

        while (container.firstChild) container.removeChild(container.firstChild);
        if (players.length === 0) return;

        var width  = container.offsetWidth || 600;
        var height = container.offsetHeight > 100 ? container.offsetHeight : 400;
        var R      = 36;
        var displaySize = R * 2;

        var svg = d3.select(container).append('svg')
            .attr('width', '100%').attr('height', '100%');

        var defs = svg.append('defs');

        // glow filter
        var filt = defs.append('filter').attr('id', 'node-glow');
        filt.append('feGaussianBlur').attr('stdDeviation', '5').attr('result', 'blur');
        var fm = filt.append('feMerge');
        fm.append('feMergeNode').attr('in', 'blur');
        fm.append('feMergeNode').attr('in', 'SourceGraphic');

        // clip paths for sprites
        players.forEach(function(p) {
            defs.append('clipPath').attr('id', 'clip-' + p.safeId)
                .append('circle').attr('r', R);
        });

        // full-mesh links
        var links = [];
        for (var i = 0; i < players.length; i++) {
            for (var j = i + 1; j < players.length; j++) {
                links.push({ source: i, target: j });
            }
        }

        var linkLayer = svg.append('g');
        var linkSel = linkLayer.selectAll('line').data(links).enter().append('line')
            .attr('stroke', '#8b5cf6')
            .attr('stroke-width', 1)
            .attr('stroke-opacity', 0.15);

        var nodeGroup = svg.append('g').selectAll('.pnode')
            .data(players).enter().append('g').attr('class', 'pnode')
            .style('cursor', 'grab');

        // outer glow ring
        var glowRing = nodeGroup.append('circle')
            .attr('r', R + 5)
            .attr('fill', 'none')
            .attr('stroke', 'rgba(139, 92, 246, 0.5)')
            .attr('stroke-width', 2)
            .attr('filter', 'url(#node-glow)');

        // rotating dashed ring
        nodeGroup.append('circle')
            .attr('r', R + 14)
            .attr('fill', 'none')
            .attr('stroke', 'rgba(139, 92, 246, 0.35)')
            .attr('stroke-width', 1)
            .attr('stroke-dasharray', '10 6')
            .attr('pointer-events', 'none')
            .attr('class', 'node-spin-ring');

        // background circle
        nodeGroup.append('circle')
            .attr('r', R)
            .attr('fill', 'rgba(28, 16, 64, 0.92)')
            .attr('stroke', '#8b5cf6')
            .attr('stroke-width', 2);

        // Sprite image: position the full sheet so the desired sprite cell
        // (inset-adjusted) lands exactly over the circle, then clip.
        // x = -(srcX / srcW) * displaySize - R  places the cell's left edge at -R.
        // width = imgW * displaySize / srcW  scales the sheet so srcW fills displaySize.
        nodeGroup.append('image')
            .attr('href', function(d) { return d.sheetUrl; })
            .attr('preserveAspectRatio', 'none')
            .attr('x', function(d) { return -(d.srcX / d.srcW) * displaySize - R; })
            .attr('y', function(d) { return -(d.srcY / d.srcH) * displaySize - R; })
            .attr('width',  function(d) { return d.imgW * displaySize / d.srcW; })
            .attr('height', function(d) { return d.imgH * displaySize / d.srcH; })
            .attr('clip-path', function(d) { return 'url(#clip-' + d.safeId + ')'; });

        // name label
        nodeGroup.append('text')
            .attr('text-anchor', 'middle')
            .attr('dy', R + 16)
            .attr('fill', '#ede8ff')
            .attr('font-size', '12px')
            .attr('font-family', 'Nunito, sans-serif')
            .attr('font-weight', '600')
            .text(function(d) { return d.name; });

        var chargeForce = d3.forceManyBody().strength(-240);
        var linkForce   = d3.forceLink(links).distance(160).strength(0.06);

        var sim = d3.forceSimulation(players)
            .force('center',    d3.forceCenter(width / 2, height / 2))
            .force('charge',    chargeForce)
            .force('collision', d3.forceCollide(R + 24))
            .force('link',      linkForce)
            .alphaTarget(0.04)
            .alphaDecay(0.01)
            .velocityDecay(0.55);

        // pulsate repulsion and link distance on independent slow sine waves
        var myGen = gen;
        d3.timer(function(elapsed) {
            if (container.__gen !== myGen) return true;
            var t = elapsed / 1000;
            chargeForce.strength(-240 + Math.sin(t * 0.45) * 90);
            linkForce.distance(160 + Math.sin(t * 0.3) * 55);
        });

        sim.on('tick', function() {
            nodeGroup.attr('transform', function(d) {
                d.x = Math.max(R + 6, Math.min(width  - R - 6, d.x));
                d.y = Math.max(R + 6, Math.min(height - R - 20, d.y));
                return 'translate(' + d.x + ',' + d.y + ')';
            });
            linkSel
                .attr('x1', function(d) { return d.source.x; })
                .attr('y1', function(d) { return d.source.y; })
                .attr('x2', function(d) { return d.target.x; })
                .attr('y2', function(d) { return d.target.y; });
        });

        // drag behaviour
        var drag = d3.drag()
            .on('start', function(event, d) {
                if (!event.active) sim.alphaTarget(0.2).restart();
                d.fx = d.x; d.fy = d.y;
                d3.select(this).style('cursor', 'grabbing');
            })
            .on('drag', function(event, d) {
                d.fx = event.x; d.fy = event.y;
            })
            .on('end', function(event, d) {
                if (!event.active) sim.alphaTarget(0.04);
                d.fx = null; d.fy = null;
                d3.select(this).style('cursor', 'grab');
            });
        nodeGroup.call(drag);

        // pulsing glow rings
        function pulseNode(el) {
            d3.select(el).transition()
                .duration(1400 + Math.random() * 600)
                .ease(d3.easeSinInOut)
                .attr('r', R + 9)
                .attr('stroke-opacity', 0.9)
                .transition()
                .duration(1400 + Math.random() * 600)
                .ease(d3.easeSinInOut)
                .attr('r', R + 4)
                .attr('stroke-opacity', 0.3)
                .on('end', function() { pulseNode(this); });
        }
        glowRing.each(function() { pulseNode(this); });

        // pulsing link opacity
        function pulseLink(el) {
            d3.select(el).transition()
                .duration(1800 + Math.random() * 1200)
                .ease(d3.easeSinInOut)
                .attr('stroke-opacity', 0.45)
                .transition()
                .duration(1800 + Math.random() * 1200)
                .ease(d3.easeSinInOut)
                .attr('stroke-opacity', 0.05)
                .on('end', function() { pulseLink(this); });
        }
        linkSel.each(function() { pulseLink(this); });

        // sonar ping — expanding ring that fades out, staggered per node
        nodeGroup.each(function(d, i) {
            var el = this;
            var myGen = gen;
            $wnd.setTimeout(function pingLoop() {
                if (container.__gen !== myGen) return;
                d3.select(el).insert('circle', ':first-child')
                    .attr('r', R + 5)
                    .attr('fill', 'none')
                    .attr('stroke', 'rgba(139, 92, 246, 0.65)')
                    .attr('stroke-width', 2)
                    .attr('pointer-events', 'none')
                    .transition()
                    .duration(2400)
                    .ease(d3.easeLinear)
                    .attr('r', R + 48)
                    .attr('stroke-opacity', 0)
                    .attr('stroke-width', 0.4)
                    .remove();
                $wnd.setTimeout(pingLoop, 3200 + Math.random() * 800);
            }, i * 1000);
        });

        container.__sim = sim;
    }-*/;

    public void setAdminMode(boolean adminMode) {
        this.isAdmin = adminMode;
    }

    public void updateCreatorControls(Room room) {
        boolean isCreator = room.getCreatedByUserId().equals(Cookie.getPlayerId())
                && room.getStatus() != GameStatus.PLAYING;
        boolean enoughPlayers = room.getPlayerNames().size() >= room.getMinPlayers();

        startGameButton.setVisible(isCreator);
        startGameButton.setEnabled(isCreator && enoughPlayers);

        if (isCreator) {
            if (!enoughPlayers) {
                int missing = room.getMinPlayers() - room.getPlayerNames().size();
                startInfoLabel.setText(I18n.m().waitingForPlayers(missing));
                startInfoLabel.setStyleName("startInfoLabel startInfoLabel-waiting");
                startInfoLabel.setVisible(true);
            } else {
                startInfoLabel.setVisible(false);
            }
        } else {
            String creatorName = room.getPlayerNames().get(room.getCreatedByUserId());
            if (creatorName == null) creatorName = I18n.c().theHost();
            startInfoLabel.setText(I18n.m().willStartTheGame(creatorName));
            startInfoLabel.setStyleName("startInfoLabel startInfoLabel-waiting");
            startInfoLabel.setVisible(true);
        }

        boolean canDelete = isCreator || isAdmin;
        deleteRoomButton.setEnabled(canDelete);
        deleteRoomButton.setVisible(canDelete);
    }

    public void refreshMessages(List<Message> messages) {
        StringBuilder output = new StringBuilder();
        String lastTimeStamp = "";
        for (Message message : messages) {
            if (!lastTimeStamp.equals(message.getTimestamp())) {
                lastTimeStamp = message.getTimestamp();
                output.append(lastTimeStamp);
            } else {
                output.append("     ");
            }
            output.append(" ").append(message.getNameSender()).append(" : ").append(message.getMessage());
            output.append("\n");
        }
        Element el = messageDisplayField.getElement();
        boolean atBottom = isScrolledToBottom(el);
        messageDisplayField.setText(output.toString());
        if (atBottom) scrollToBottom(el);
    }

    private native boolean isScrolledToBottom(Element el) /*-{
        return el.scrollHeight - el.scrollTop - el.clientHeight < 6;
    }-*/;

    private native void scrollToBottom(Element el) /*-{
        el.scrollTop = el.scrollHeight;
    }-*/;

    public void showRoomName(String roomName) {
        String fullTitle = I18n.c().roomPrefix() + " " + roomName;
        roomTitle.setInnerText(fullTitle);
        int len = fullTitle.length();
        if (len <= 12) {
            roomTitle.setClassName("room-title-short");
        } else if (len <= 22) {
            roomTitle.setClassName("room-title-medium");
        } else {
            roomTitle.setClassName("room-title-long");
        }
    }
}