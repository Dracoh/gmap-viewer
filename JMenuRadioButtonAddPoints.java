import java.awt.*;
import javax.swing.*;
import java.awt.event.*;
import java.awt.*;
import java.io.*;
import java.util.*;
import java.awt.image.*;
import javax.imageio.*;
import java.beans.*; //Property change stuff
import javax.swing.table.*;
import com.sun.image.codec.jpeg.*;
import java.net.*;
import javax.imageio.ImageIO;
import java.awt.geom.*;

/**
 * Class which handles allowing the user to draw points on the map. 
 *
 */
class JMenuRadioButtonAddPoints extends JMenuRadioButtonAction {

   static final long serialVersionUID = 42643948;

   /**
    * Constructor for creation of Radio button
    * @param registeredObject
    */
   public JMenuRadioButtonAddPoints(GUI registeredObject) {
      super("Add Points",registeredObject);
   }
   
   /**
    * run method for class
    */
   public void run() {
      GUI gui = (GUI) registeredObject;
      GPane pane = gui.getTopPane();
      pane.setMode(GPane.DRAW_MARKER_MODE);
      super.setSelected(true);
      gui.getNotifier().firePaneEvent(this);
      gui.getProgressMeter().getPanel().repaint();
   }

   /**
    * Pane event method for class.
    */
   public void paneEvent(Object object){
      GUI gui = (GUI)registeredObject;
      GPane pane = gui.getTopPane();
      if(pane == null) return;
      super.setSelected(pane.getMode() == GPane.DRAW_MARKER_MODE);
   }

}