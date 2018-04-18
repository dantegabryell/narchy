package spacegraph.space2d.widget.adapter;

import spacegraph.SpaceGraph;
import spacegraph.space2d.container.grid.Gridding;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLFrameHyperlinkEvent;
import java.awt.*;
import java.io.IOException;

public class HTMLSurface extends AWTSurface {

    public static void main(String[] args) {
        SpaceGraph.wall(800, 800).put(
            new Gridding(new HTMLSurface("http://java.com", 800, 800)), 1, 1
        );
    }


    public HTMLSurface(String url, int w, int h) {
        super(new HTMLView(url), w, h);
    }

    static class HTMLView extends JFrame {


        private final JEditorPane ed;

        public HTMLView(String initialURL) {

            JLabel lblURL = new JLabel("URL");
            final JTextField txtURL = new JTextField(initialURL, 30);
            JButton btnBrowse = new JButton("Browse");

            JPanel panel = new JPanel();
            panel.setLayout(new FlowLayout());
            panel.add(lblURL);
            panel.add(txtURL);
            panel.add(btnBrowse);


            btnBrowse.addActionListener(e ->
                go(txtURL.getText().trim())
            );
            //sp = new JScrollPane();

            this.setLayout(new BorderLayout());

            getContentPane().add(panel, BorderLayout.NORTH);


            this.setSize(500, 350);
            this.setVisible(true);

            ed = new JEditorPane();
            ed.setEditable(false);

            ed.addHyperlinkListener(e -> {
                if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                    JEditorPane pane = (JEditorPane) e.getSource();
                    if (e instanceof HTMLFrameHyperlinkEvent) {
                        HTMLFrameHyperlinkEvent evt = (HTMLFrameHyperlinkEvent) e;
                        HTMLDocument doc = (HTMLDocument) pane.getDocument();
                        doc.processHTMLFrameHyperlinkEvent(evt);
                    } else {
                        try {
                            pane.setPage(e.getURL());
                        } catch (Throwable t) {
                            t.printStackTrace();
                        }
                    }
                }
            });
            //ed.setAutoscrolls(true);
            getContentPane().add(new JScrollPane(ed), BorderLayout.CENTER);


            go(initialURL);


        }

        public void go(String url) {
            try {

                ed.setPage(url);
                repaint();

            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }


}
