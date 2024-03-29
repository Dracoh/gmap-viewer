/**
 * This file contains the GPane class
 */

import java.awt.*;
import javax.swing.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;

/**
 * The GPane class.
 *
 * This class handles the work of storing and tracking all the information
 * needed to build a map. This includes zoom level, cached zoom level, width,
 * height, and center position. It also controls and tracks the selected area,
 * and handles the task of painting images to the screen by overriding the
 * paintComponent() method.
 */
public class GPane extends JPanel implements ActionListener, KeyListener, ComponentListener, MouseListener, MouseMotionListener, Cloneable, GMapListener, MouseWheelListener{

   //GMap object
   private GUI gui;

   //image AND icon object
   transient private BufferedImage image;
   private JLabel label;

   //stuff to keep track of
   private GPhysicalPoint center;
   private int zoom;

   //mouse rectangle parameters
   private Rectangle mouseRectanglePosition;

   //display cache variables
   private boolean showCachedZoom;
   private int showCachedZoomLevel;

   //this alphacomposite is controls transparency
   transient private AlphaComposite opacity30 = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f);
   transient private AlphaComposite opacity70 = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f);

   //a thread for drawing stuff
   transient private DrawThread drawingThread;

   /**
    * Selection mode.
    *
    * Clicking the mouse starts a new selection rectangle.
    */
   public static final int SELECTION_MODE = 0;

   /**
    * Dragging mode.
    *
    * Clicking the mouse will move the map.
    */
   public static final int DRAGGING_MODE = 1;

   /**
    * Draw line mode.
    *
    * Clicking the mouse in two places will draw a line.
    */
   public static final int DRAW_LINE_MODE = 2;

   /**
    * Draw marker mode.
    *
    * Clicking the mouse will create a new marker.
    */
   public static final int DRAW_MARKER_MODE = 3;

   /**
    * Draw string mode.
    *
    * Clicking the mouse wll allow the user to type in text.
    */
   public static final int DRAW_STRING_MODE = 4;

   /**
    * Calculate distance mode.
    *
    * Clicking the mouse in two places will calculate and display the distance.
    */
   public static final int DISTANCE_MODE = 5;

   /**
    * Draw image mode.
    *
    * Clicking the mouse in two places will place an image on the map.
    */
   public static final int IMAGE_MODE = 6;

   private boolean smartClick;
   private int mode;

   //buffered image
   transient private BufferedImage googleLogo;

   //mouse depressed
   private boolean mouseIsPressed;

   private boolean downloadBorderDataMode;

   /**
    * Constructor.
    *
    * Sets the pane layout, stores data elements, and fires a pane event to all
    * paneListeners.
    *
    * @param gui
    * @param center
    * @param zoom
    * @param showCachedZoom
    * @param showCachedZoomLevel
    * @param mode
    */
   public GPane(GUI gui, GPhysicalPoint center, int zoom, boolean showCachedZoom, int showCachedZoomLevel, int mode){
      //get gui.getGMap() and registered objects
      this.gui = gui;

      //mouse state
      mouseIsPressed = false;

      //zoom
      this.zoom = zoom;
      this.center = center;

      //add this stuff to pane
      label = new JLabel();
      setLayout(null);
      add(label);

      //set mode
      this.mode = mode;

      //load google logo
      googleLogo = LibGUI.loadImage("images/google.png");


      //draw it
      draw();

      //showCachedZoom
      this.showCachedZoomLevel = showCachedZoomLevel;
      this.showCachedZoom = showCachedZoom;

      //start pane listener
      //initializePaneListener();

      //add component listener
      addComponentListener(this);
      addMouseListener(this);
      addMouseMotionListener(this);
      addMouseWheelListener(this);
      addKeyListener(this);

      //smart click
      smartClick = false;

      //initialize draw thread to null
      drawingThread = null;

      //fire pane listener event
      gui.getNotifier().firePaneEvent(this);
   }

   /**
    * Default constructor.
    *
    * @param gui
    */
   public GPane(GUI gui){
      this(gui, new GPhysicalPoint(29.8265419861086, -82.35763549804688), 10, false, (GPhysicalPoint.MIN_ZOOM - 1), SELECTION_MODE);
   }

   /**
    * Get a new image from GMap and repaint the screen.
    */
   public void draw(){
      drawThreadScheduler();
   }

   //a bit to determine if at least one more recent draw has been
   //suppressed
   private boolean drawWaiting;

   private void drawThreadScheduler(){
      boolean suppressThread = (drawingThread != null);
      if(suppressThread) suppressThread = drawingThread.isAlive();

      if(!suppressThread){
         drawWaiting = false;
         drawingThread = new DrawThread(this);
         drawingThread.start();
      }else{
         drawWaiting = true;
      }
   }

   void drawThreadWaiting(){
      if(drawWaiting){
         drawWaiting = false;
         drawingThread = new DrawThread(this);
         drawingThread.start();
      }
   }

   private class DrawThread extends Thread{
      private GPane parent;
      public DrawThread(GPane parent){
         this.parent = parent;
      }
      public void run(){
         try{
            //set cursor to hourglass
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

            //System.out.println("fired - "+getSize().width+", "+getSize().height);
            //check to make sure size is valid
            if(getSize().width == 0 || getSize().height == 0) return;

            //set cached zoom level
            int useCachedZoomLevel = showCachedZoomLevel;
            if(!showCachedZoom) useCachedZoomLevel = (GPhysicalPoint.MIN_ZOOM - 1);

            //timer start
            long start = LibGUI.getTime();

            //get location
            Point centerPixels = center.getPixelPoint(zoom);
            int x = centerPixels.x - (getSize().width/2);
            int y = centerPixels.y - (getSize().height/2);

            //set the bit to determine if we need new image memory
            boolean newImageMemory = (image == null);
            if(!newImageMemory) newImageMemory = (image.getWidth() != getSize().width || image.getHeight() != getSize().height);

            //make new image if necessary
            if(newImageMemory){
               image = new BufferedImage(getSize().width, getSize().height,BufferedImage.TYPE_INT_ARGB);
            }
            //make graphics object
            Graphics2D g2d = (Graphics2D)image.createGraphics();


            //empty data source
            gui.getGMap().getGDataSource().abortQueue();

            gui.getGMap().paintAsynchronousImage(image, x, y, getSize().width, getSize().height, zoom, useCachedZoomLevel, parent);

            //google logo
            Composite temp = g2d.getComposite();
            g2d.setComposite(opacity70);
            g2d.drawImage(googleLogo, image.getWidth()-googleLogo.getWidth(), image.getHeight()-googleLogo.getHeight(), googleLogo.getWidth(), googleLogo.getHeight(),null);
            g2d.setComposite(temp);

            if(!gui.getProgressMeter().release(parent)) return ;

            //timer stop
            System.out.println("Draw time = " + (LibGUI.getTime() - start));

   //         //TEMP - DRAW TICK LINES
   //         BufferedImage toDraw = new BufferedImage(image.getWidth(),image.getHeight(),BufferedImage.TYPE_INT_RGB);
   //         Graphics2D g = toDraw.createGraphics();
   //
   //         g2d.drawImage(image, 0, 0, image.getWidth(), getHeight(), null);
   //         g2d.drawLine(getSize().width/2, 0, getSize().width/2, getSize().height);
   //         g2d.drawLine(0, getSize().height/2, getSize().width, getSize().height/2);
   //
   //         //update icon bounds
   //         label.setBounds(0,0,getSize().width, getSize().height);
   //END TEMP
            //image = toDraw;
            updateScreen();

            //set cursor to hourglass
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

            //download adjacent
            if(!mouseIsPressed) gui.getGMap().getGDataSource().downloadQueue();

            //update flag
            parent.drawingThread = null;
         }catch(Exception exception){
            gui.getMessagePanel().setException(exception);
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
         }

         //notify listener
         gui.getNotifier().firePaneEvent(this);
         parent.repaint();

         //see if a new draw is needed to keep the screen up to date
         drawThreadWaiting();
      }
   }

   /**
    * Makes an asynchronous call to repaint
    */
   public void updateScreen(){
      //this.paintImmediately(0,0,this.getWidth(), this.getHeight());
      this.repaint();
   }

   /**
    * Using an image already retrieved from the GMap, does the work of painting
    * this image (and the selection overlay) to the screen.
    *
    * @param g the <tt>Graphics</tt> object
    */
   protected void paintComponent(Graphics g) {
      //call superclass, although its not necessary cause this is not transparent
      super.paintComponent(g);

      Graphics2D g2d = (Graphics2D)g;

      //draw overlay
      g2d.drawImage(image, 0, 0, getWidth(), getHeight(), null);

      //rectangle
      if(mouseRectanglePosition != null){
         //draw transparent white
         g2d.setColor(Color.WHITE);
         Composite temp = g2d.getComposite();
         g2d.setComposite(opacity30);
         g2d.fillRect(mouseRectanglePosition.x,mouseRectanglePosition.y,mouseRectanglePosition.width,mouseRectanglePosition.height);
         g2d.setComposite(temp);
         //draw border
         g2d.setColor(new Color(110,110,110));
         g2d.drawRect(mouseRectanglePosition.x,mouseRectanglePosition.y,mouseRectanglePosition.width,mouseRectanglePosition.height);
      }
   }

   /**
    * Gets the center.
    * @return the center
    */
   public GPhysicalPoint getCenter(){
      return center;
   }

   /**
    * Gets the upper left pixel of map.
    * @return the upper left pixel of the map.
    */
   public Point getUpperLeftPixel(){
      Point centerPixel = center.getPixelPoint(zoom);
      int x = centerPixel.x - (getSize().width/2);
      int y = centerPixel.y - (getSize().height/2);
      return new Point(x,y);
   }

   /**
    * Gets the center as a pixel at this zoom level.
    * @return the center as a pixel at this zoom level.
    */
   public Point getCenterPixel(){
      return center.getPixelPoint(zoom);
   }

   /**
    * Gets <tt>Rectangle</tt> corresponding to the selected area.
    * @return the <tt>Rectangle</tt> corresponding to the selected area.
    */
   public Rectangle getMouseRectanglePosition(){
      return mouseRectanglePosition;
   }

   /**
    * Gets the BufferedImage of the visible map, without the selection rectangle.
    * @return The visible map.
    */
   public BufferedImage getImage(){
      return image;
   }

   /**
    * Computes and returns the centered mouse rectangle position.
    * Mouse rectangle position is the selected area.
    * @return the <tt>CentertedRectangle</tt>
    */
   public CenteredRectangle getMouseRectanglePositionCentered(){
      if(mouseRectanglePosition == null) return null;
      Point ul = getUpperLeftPixel();
      int x = ul.x + mouseRectanglePosition.x + mouseRectanglePosition.width/2;
      int y = ul.y + mouseRectanglePosition.y + mouseRectanglePosition.height/2;
      return new CenteredRectangle(new GPhysicalPoint(x,y,zoom),mouseRectanglePosition.width,mouseRectanglePosition.height);
   }

   /**
    * Gets a clone of the ScreenDimensionsCentered <tt>CenteredRectangle</tt>.
    * @return the cloned <tt>CentertedRectangle</tt>
    */
   public CenteredRectangle getScreenDimensionsCentered(){
      return new CenteredRectangle((GPhysicalPoint)center.clone(),getSize().width,getSize().height);
   }

   /**
    * Gets the current zoom level.
    * @return the current zoom level.
    */
   public int getZoom(){
      return zoom;
   }

   /**
    * Gets the zoom level of the cached area shown.
    * @return the zoom level of the cached area shown.
    */
   public int getShowCachedZoomLevel(){
      return showCachedZoomLevel;
   }

   /**
    * Gets whether to show the cached area at this zoom level.
    * @return whether to show the cached area at this zoom level.
    */
   public boolean getShowCachedZoom(){
      return showCachedZoom;
   }

   /**
    * Gets the mode.
    * @return the mode.
    */
   public int getMode(){
      return mode;
   }

   /**
    * Gets the status of smartclick. Smart click is a feature that
    * automatically maps your click on the image to the center
    * of a given road.
    * @return smartClick
    */
   public boolean getSmartClick(){
      return smartClick;
   }


   /**
    * Sets the center using a physical point.
    * @param center
    */
   public void setCenter(GPhysicalPoint center){
      this.center = center;
      draw();
   }

   /**
    * Sets the center using a pixel point.
    * @param center
    */
   public void setCenterPixel(Point center){
      this.center.setPixelPoint(center, zoom);
      draw();
   }

   /**
    * Sets the zoom level; checks to make sure it is in range.
    * @param zoom
    */
   public void setZoom(int zoom){
      if(zoom < GPhysicalPoint.MIN_ZOOM || zoom > GPhysicalPoint.MAX_ZOOM) return;
      //if the pane show cached zoom is more than 4 away, turn it off
      if(getShowCachedZoom() && (showCachedZoomLevel >= zoom || zoom - showCachedZoomLevel > 4))
         setShowCachedZoom(false);
      this.zoom = zoom;
      draw();
   }

   /**
    * Sets the zoom level of the cached area shown.
    * @param showCachedZoomLevel
    */
   public void setShowCachedZoomLevel(int showCachedZoomLevel){
      this.showCachedZoomLevel = showCachedZoomLevel;
      draw();
   }

   /**
    * Sets whether to show the cached area.
    * @param showCachedZoom
    */
   public void setShowCachedZoom(boolean showCachedZoom){
      this.showCachedZoom = showCachedZoom;
      draw();
   }

   /**
    * Sets smart click on or off. Smart click is a feature that
    * automatically maps your click on the image to the center
    * of a given road.
    * @param smartClick
    */
   public void setSmartClick(boolean smartClick){
      this.smartClick = smartClick;
   }

   /**
    * Sets the mode.
    * @param mode
    */
   public void setMode(int mode){
      //clean up tracking variables for mouse actions
      clickCount = 0;
      gui.getGMap().getGDraw().remove(tempMarkerA);
      mouseRectanglePosition = null;
      this.mode=mode;
      draw();
   }

   /**
    * @see java.awt.event.ComponentListener#componentHidden(ComponentEvent)
    */
   public void componentHidden(ComponentEvent e){}

   /**
    * @see java.awt.event.ComponentListener#componentMoved(ComponentEvent)
    */
   public void componentMoved(ComponentEvent e){}

   /**
    * @see java.awt.event.ComponentListener#componentResized(ComponentEvent)
    */
   public void componentResized(ComponentEvent e){draw();}

   /**
    * @see java.awt.event.ComponentListener#componentShown(ComponentEvent)
    */
   public void componentShown(ComponentEvent e){gui.getNotifier().firePaneEvent(this);}

   //mouse methods - use e.getX()
   private Point mouseLocation = new Point(0,0);
   private Point clickLocation = new Point(0,0);
   private Dimension mouseOffset = new Dimension(0,0);

   /**
    * @see java.awt.event.MouseMotionListener#mouseMoved(MouseEvent)
    */
   public void mouseMoved(MouseEvent e){
      int mouseX = e.getX() + mouseOffset.width;
      int mouseY = e.getY() + mouseOffset.height;

      mouseLocation.x = mouseX;
      mouseLocation.y = mouseY;
   }

   private boolean mouseDraggedThisClick = false;

   /**
    * @see java.awt.event.MouseMotionListener#mouseDragged(MouseEvent)
    */
   public void mouseDragged(MouseEvent e){
      int m = e.getModifiers();
      if(m == 16){
         int mouseX = e.getX() + mouseOffset.width;
         int mouseY = e.getY() + mouseOffset.height;

         //center
         Point original = getCenterPixel();

         if(mode == SELECTION_MODE){
            //check for nulls
            if(mouseRectanglePosition == null || clickLocation == null) return ;

            //update dragged boolean
            mouseDraggedThisClick = true;
            //do computations relating to selection
            mouseRectanglePosition.x = Math.min(clickLocation.x,mouseX);
            mouseRectanglePosition.y = Math.min(clickLocation.y,mouseY);
            mouseRectanglePosition.width = Math.max(clickLocation.x,mouseX) - mouseRectanglePosition.x;
            mouseRectanglePosition.height = Math.max(clickLocation.y,mouseY) - mouseRectanglePosition.y;
            updateScreen();
         }else if(mode == DRAGGING_MODE){
            //do computations relating to dragging the center
            if(original == null || center == null || clickLocation == null) return ;
            center.setPixelPoint(new Point(original.x + (clickLocation.x - e.getX()), original.y + (clickLocation.y - e.getY())), zoom);
            clickLocation.x = e.getX();
            clickLocation.y = e.getY();
            draw();
         }
      }
   }

   /**
    * @see java.awt.event.MouseListener#mouseClicked(MouseEvent)
    */
   public void mouseClicked(MouseEvent e){
      //single left click
      if(e.getModifiers() == 16){
         //single left click

         //get upper left coord
         Point centerPixels = center.getPixelPoint(zoom);
         int x = centerPixels.x - (getSize().width/2);
         int y = centerPixels.y - (getSize().height/2);

         if(mode == SELECTION_MODE || mode == DRAGGING_MODE){
            //get the index of what was clicked
            int clicked = gui.getGMap().getGDraw().inside(new Point(e.getX(),e.getY()), new GPhysicalPoint(x,y,zoom), zoom);
            //get the object that was clicked
            GDrawableObject clickedObj = null;

            //remove all selected objects
            gui.getGMap().getGDraw().getSelected().removeAll();

            //figure out if we're selecting something
            if(clicked != -1){
               clickedObj = gui.getGMap().getGDraw().get(clicked);
               gui.getGMap().getGDraw().getSelected().add(clickedObj);
            }

            //redraw
            draw();
         }
      }
   }

   /**
    * @see java.awt.event.MouseListener#mouseEntered(MouseEvent)
    */
   public void mouseEntered(MouseEvent e){}

   /**
    * @see java.awt.event.MouseListener#mouseExited(MouseEvent)
    */
   public void mouseExited(MouseEvent e){}

   private int clickCount = 0;
   private GPhysicalPoint tempA;
   private GPhysicalPoint tempB;
   private GMarker tempMarkerA;
   private GDraw gDrawToAdd;
   private GText tempDistance;
   private double runningDistance;

   /**
    * @see java.awt.event.MouseListener#mousePressed(MouseEvent)
    */
   public void mousePressed(MouseEvent e){
      //get the focus whenever its clicked
      this.requestFocusInWindow();

      //update mouse dragged
      mouseIsPressed = true;
      mouseDraggedThisClick = false;

      int mouseX = e.getX() + mouseOffset.width;
      int mouseY = e.getY() + mouseOffset.height;

      clickLocation.x = mouseX;
      clickLocation.y = mouseY;

      //smart click
      if(smartClick) clickLocation = GLib.smartPoint(image, clickLocation, 0.0, 25);

      int m = e.getModifiers();
      if(m == 16){

         //left click
         if(e.getClickCount() == 1){
            //get this click location
            Point c = getCenterPixel();
            c.x += clickLocation.x - (getSize().width/2);
            c.y += clickLocation.y - (getSize().height/2);

            //single left click
            if(mode == SELECTION_MODE)
               mouseRectanglePosition = new Rectangle(clickLocation.x,clickLocation.y,clickLocation.x,clickLocation.y);
            else if(mode == DRAW_LINE_MODE && clickCount == 0){
               clickCount++;
               tempA = new GPhysicalPoint(c.x, c.y,zoom);
               tempMarkerA = new GMarker(tempA);
               gui.getGMap().getGDraw().add(tempMarkerA);
               draw();
            }
            else if(mode == DRAW_LINE_MODE && clickCount >= 1){
               //if this is the second click, set up the gDraw
               if(clickCount == 1){
                  gDrawToAdd = new GDraw();
                  gui.getGMap().getGDraw().add(gDrawToAdd);
                  gui.getGMap().getGDraw().remove(tempMarkerA);
               }
               //increment click count and add another line
               clickCount++;
               tempB = new GPhysicalPoint(c.x, c.y,zoom);
               gDrawToAdd.add(new GLine(tempA, tempB));
               tempA = (GPhysicalPoint)tempB.clone();
               draw();
            }
            else if(mode == IMAGE_MODE && clickCount == 0){
               clickCount++;
               tempA = new GPhysicalPoint(c.x, c.y,zoom);
               tempMarkerA = new GMarker(tempA);
               gui.getGMap().getGDraw().add(tempMarkerA);
               draw();
            }
            else if(mode == IMAGE_MODE && clickCount >= 1){
               //if this is the second click, set up the gDraw
               if(clickCount == 1){
                  gDrawToAdd = new GDraw();
                  gui.getGMap().getGDraw().add(gDrawToAdd);
                  gui.getGMap().getGDraw().remove(tempMarkerA);
               }
               //increment click count and add another line
               clickCount = 0;
               tempB = new GPhysicalPoint(c.x, c.y,zoom);

               //set extension
               String extension = "png";

               //file chooser
               String filename = File.separator+extension;
               JFrame frame= new JFrame();
               JFileChooser fileChooser = new JFileChooser(new File(filename));

               // Show save dialog; this method does not return until the dialog is closed
               fileChooser.showSaveDialog(frame);
               File outputFile = fileChooser.getSelectedFile();
               BufferedImage loadedImage = LibGUI.loadImage(outputFile.getPath());

               if(outputFile != null && loadedImage != null) gDrawToAdd.add(new GImage(tempA, tempB, loadedImage));
               draw();
            }
            else if(mode == DISTANCE_MODE && clickCount == 0){
               clickCount = 1;
               runningDistance = 0.0;
               tempA = new GPhysicalPoint(c.x, c.y,zoom);
               tempMarkerA = new GMarker(tempA);
               gui.getGMap().getGDraw().add(tempMarkerA);
               draw();
            }
            else if(mode == DISTANCE_MODE && clickCount >= 1){
               //if this is the second click, set up the gDraw
               if(clickCount == 1){
                  gDrawToAdd = new GDraw();
                  gui.getGMap().getGDraw().add(gDrawToAdd);
               }

               //increment click count and draw another line
               clickCount++;
               tempB = new GPhysicalPoint(c.x, c.y,zoom);
               gDrawToAdd.add(new GLine(tempA, tempB));
               gDrawToAdd.remove(tempDistance);
               runningDistance += GLib.computeDistance(tempA, tempB);
               tempDistance = new GText(tempB, ""+Math.round(runningDistance*1000.0)/1000.0);
               gDrawToAdd.add(tempDistance);
               tempA = (GPhysicalPoint)tempB.clone();
               draw();
            }
            else if(mode == DRAW_MARKER_MODE){
               clickCount = 0;
               tempB = new GPhysicalPoint(c.x, c.y,zoom);
               gui.getGMap().getGDraw().add(new GMarker(tempB));
               draw();
            }
            else if(mode == DRAW_STRING_MODE){
               clickCount = 0;
               tempA = new GPhysicalPoint(c.x, c.y,zoom);
               tempMarkerA = new GMarker(tempA);
               gui.getGMap().getGDraw().add(tempMarkerA);
               draw();

               String in = (String)JOptionPane.showInputDialog(gui.frame, "Enter the text to place on the map.","Add Text",JOptionPane.PLAIN_MESSAGE,null,null,"");
               gui.getGMap().getGDraw().remove(tempMarkerA);

               if(in != null){
                  tempB = new GPhysicalPoint(c.x, c.y,zoom);
                  gui.getGMap().getGDraw().add(new GText(tempB, in));
               }
               draw();
            }
         }
         else if(e.getClickCount() == 2){
            //double left click
            Point c = getCenterPixel();
            c.x += clickLocation.x - (getSize().width/2);
            c.y += clickLocation.y - (getSize().height/2);
            center.setPixelPoint(c,zoom);
            draw();
         }
      }
      else if(m == 18 || m == 17){
         //left click with control or shift
      }
      else if(m == 4){
         //right click
         gui.getTabbedPane().showPopupMenu(e.getX(), e.getY());
      }
   }

   /**
    * @see java.awt.event.MouseListener#mouseReleased(MouseEvent)
    */
   public void mouseReleased(MouseEvent e){
      mouseIsPressed = false;

      int m = e.getModifiers();
      if(m == 16){

         //if we didn't drag the map on this clip, do this stuff
         if(!mouseDraggedThisClick){
            mouseRectanglePosition = null;
            gui.getGMap().getGDataSource().downloadQueue();
         }

         //if there is a selection rectangle, check and see what is selected
         if(mouseRectanglePosition != null){
            //get location
            Point centerPixels = center.getPixelPoint(zoom);
            int x = centerPixels.x - (getSize().width/2);
            int y = centerPixels.y - (getSize().height/2);
            GPhysicalPoint upperLeft = new GPhysicalPoint(x,y,zoom);

            //clear the selection
            gui.getGMap().getGDraw().getSelected().removeAll();

            //loop through each object in gdraw and see if its contained by selection
            int size = gui.getGMap().getGDraw().getSize();
            for(int i=0;i<size;i++){
               if(mouseRectanglePosition.contains(gui.getGMap().getGDraw().get(i).getRectangle(upperLeft, zoom))){
                  //add this one
                  gui.getGMap().getGDraw().getSelected().add(gui.getGMap().getGDraw().get(i));
               }
            }
            //update selection
            updateSelection();

            draw();
         }

         //download adjacent
         gui.getGMap().getGDataSource().downloadQueue();

         //update
         updateScreen();
      }
  }

   /**
    * @see java.awt.event.KeyListener#keyTyped(KeyEvent)
    */
   public void keyTyped(KeyEvent k){
   }

   /**
    * @see java.awt.event.KeyListener#keyReleased(KeyEvent)
    */
   public void keyReleased(KeyEvent k){
   }

   /**
    * @see java.awt.event.KeyListener#keyPressed(KeyEvent)
    */
   private static final double KEY_MOVE_INTERVAL = 0.000093291274983;

   public void keyPressed(KeyEvent keyEvent){
      int k = keyEvent.getKeyCode();

      double interval = KEY_MOVE_INTERVAL*Math.pow(2,zoom-1);
      int selectedSize = gui.getGMap().getGDraw().getSelected().getSize();
      Point p = center.getPixelPoint(getZoom());
      int jumpDistance = 10;

      if(k == 37){
         //left
         if(selectedSize != 0) moveSelectedObjects(-1.0*interval,0);
         else p.x += (jumpDistance*-1);
         center.setPixelPoint(p,getZoom());
         draw();
      }else if(k == 38){
         //up
         if(selectedSize != 0) moveSelectedObjects(0,1.0*interval);
         else p.y += (jumpDistance*-1);
         center.setPixelPoint(p,getZoom());
         draw();
      }else if(k == 39){
         //right
         if(selectedSize != 0) moveSelectedObjects(1.0*interval,0);
         else p.x += (jumpDistance*1);
         center.setPixelPoint(p,getZoom());
         draw();
      }else if(k == 40){
         //down
         if(selectedSize != 0) moveSelectedObjects(0,-1.0*interval);
         else p.y += (jumpDistance*1);
         center.setPixelPoint(p,getZoom());
         draw();
      }else if(k == 33 || (k == 10 && keyEvent.getModifiers() == 0)){
         //zoom in
         setZoom(getZoom()-1);
         draw();
      }else if(k == 34 || (k == 10 && keyEvent.getModifiers() == 1)){
         //zoom out
         setZoom(getZoom()+1);
         draw();
      }else if(k == 127){
         //delete
         deleteSelectedObjects();
         draw();
      }

   }

   private void updateSelection(){
      //boolean[] isType = {true, true, true, true};
      //Object[] objType[] = {}

      //gui.getGMap().getGDraw().getSelected().getSize();
   }

   private void moveSelectedObjects(double latitude, double longitude){
      int size = gui.getGMap().getGDraw().getSelected().getSize();
      for(int i=0;i<size;i++){
         GDrawableObject gd = (GDrawableObject)gui.getGMap().getGDraw().getSelected().get(i);
         gd.move(latitude, longitude);
      }
   }

   private void deleteSelectedObjects(){
      int size = gui.getGMap().getGDraw().getSelected().getSize();
      for(int i=0;i<size;i++){
         GDrawableObject gd = (GDrawableObject)gui.getGMap().getGDraw().getSelected().get(i);
         gui.getGMap().getGDraw().remove(gd);
      }
   }

   /**
    * Creates a deep copy of this GPane.
    */
   public Object clone(){
      return new GPane(gui, (GPhysicalPoint)center.clone(), zoom, showCachedZoom, showCachedZoomLevel, mode);
   }

   //gui.getGMap() listener
   private int gmapCompleted;
   private int gmapTaskSize;
   private int messageNumber;

   /**
    * Sets the number of completed units for the current task tracked by the
    * ProgressMeter.
    */
   public void updateGMapCompleted(int completed){
      this.gmapCompleted = completed;
      gui.getProgressMeter().setPercent(ProgressMeter.computePercent(gmapCompleted,gmapTaskSize),this);
   }

   /**
    * Sets the total number of units for the current task tracked by the
    * ProgressMeter.
    */
   public void updateGMapTaskSize(int size){
      this.gmapTaskSize = size;
      gui.getProgressMeter().setPercent(ProgressMeter.computePercent(gmapCompleted,gmapTaskSize),this);
   }

   /**
    * Sets the message for the current task tracked by the ProgressMeter.
    */
   public void updateGMapMessage(int messageNumber){
      if(this.messageNumber != messageNumber){
         this.messageNumber = messageNumber;
         String message;
         if(messageNumber == GMap.MESSAGE_DOWNLOADING){
            gui.getProgressMeter().grab(this);
            gui.getProgressMeter().registerThread(drawingThread, this);
            message = "Downloading data...";
         }
         else if(messageNumber == GMap.MESSAGE_PAINTING){
            message = "Painting image...";
         }
         else{
            message = "Working...";
         }
         gui.getProgressMeter().setMessage(message,this);
      }
   }

   /**
    * Updates the screen.
    */
   public void updateGMapPainting(){
      updateScreen();
   }

   /**
    * Gets the stop flag, asynchrounously.
    */
   public boolean asynchronousGMapStopFlag(){
      return gui.getProgressMeter().getStopFlag();
   }

   /**
    * Action dispatcher method from meubar
    * @see java.awt.event.ActionListener#actionPerformed(ActionEvent)
    */
   public void actionPerformed(ActionEvent e){
      Object sourceObject = e.getSource();
      //dispatch actions
      if(sourceObject instanceof JMenuAction){
         JMenuAction sourceMenuAction = (JMenuAction)sourceObject;
         sourceMenuAction.start();
      }

      //dispatch radio button actions
      if(sourceObject instanceof JMenuRadioButtonAction){
         JMenuRadioButtonAction sourceMenuAction = (JMenuRadioButtonAction)sourceObject;
         sourceMenuAction.start();
      }
   }

   /**
    * @see java.awt.event.MouseWheelListener#mouseWheelMoved(java.awt.event.MouseWheelEvent)
    */
   public void mouseWheelMoved(MouseWheelEvent e) {
      int notches = e.getWheelRotation();

      if(notches < 0) {
         // Mouse wheel moved up
         this.gui.getTopPane().setZoom(this.gui.getTopPane().getZoom() - 1);
      } else {
         // Mouse wheel moved down
         this.gui.getTopPane().setZoom(this.gui.getTopPane().getZoom() + 1);
      }
   }
}
