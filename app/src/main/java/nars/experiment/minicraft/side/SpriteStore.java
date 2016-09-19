package nars.experiment.minicraft.side;


import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URL;
import java.util.HashMap;

public abstract class SpriteStore {
	/** The single instance of this class */
	protected static SpriteStore single;
	
	/**
	 * Get the single instance of this class
	 *
	 * @return The single instance of this class
	 */
	public static SpriteStore get() {
		if (single == null) {
			if (GraphicsHandler.awtMode) {
				single = new AwtSpriteStore();
			} else {
				// android!
			}
		}
		return single;
	}

	public static class AwtSpriteStore extends SpriteStore {

		@Override
		public Sprite loadSprite(String ref) {
			// otherwise, go away and grab the sprite from the resource
			// loader
			BufferedImage sourceImage = null;

			try {
				// The ClassLoader.getResource() ensures we get the sprite
				// from the appropriate place, this helps with deploying the game
				// with things like webstart. You could equally do a file look
				// up here.
				URL url = this.getClass().getResource(ref);

				if (url == null) {
					fail("Can't find ref: " + ref);
				}

				// use ImageIO to read the image in
				sourceImage = ImageIO.read(url);
			} catch (IOException e) {
				fail("Failed to load: " + ref);
			}

			// create an accelerated image of the right size to store our sprite in
			GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
					.getDefaultScreenDevice().getDefaultConfiguration();
			Image image = gc.createCompatibleImage(sourceImage.getWidth(), sourceImage.getHeight(),
					Transparency.BITMASK);

			// draw our source image into the accelerated image
			image.getGraphics().drawImage(sourceImage, 0, 0, null);

			// create a sprite, add it the cache then return it
			Sprite sprite = (Sprite) new AwtSprite(image, ref);
			return sprite;
		}

		/**
		 * Utility method to handle resource loading failure
		 *
		 * @param message
		 *            The message to display on failure
		 */
		private void fail(String message) {
			// we're pretty dramatic here, if a resource isn't available
			// we dump the message and exit the game
			System.err.println(message);
			System.exit(1);
		}
	}

	/**
	 * A sprite to be displayed on the screen. Note that a sprite
	 * contains no state information, i.e. its just the image and
	 * not the location. This allows us to use a single sprite in
	 * lots of different places without having to store multiple
	 * copies of the image.
	 *
	 * @author Kevin Glass
	 */
	public static class AwtSprite implements Sprite {
		private static final long serialVersionUID = 1L;

		/** The image to be drawn for this sprite */
		transient public Image image;
		public String ref;

		// for serialization loading
		public AwtSprite() {
			AwtSprite s = (AwtSprite) SpriteStore.get().getSprite(ref);
			this.image = s.image;
		}

		/**
		 * Create a new sprite based on an image
		 *
		 * @param image
		 *            The image that is this sprite
		 */
		public AwtSprite(Image image, String ref) {
			this.image = image;
			this.ref = ref;
		}

		/**
		 * Get the width of the drawn sprite
		 *
		 * @return The width in pixels of this sprite
		 */
		public int getWidth() {
			return image.getWidth(null);
		}

		/**
		 * Get the height of the drawn sprite
		 *
		 * @return The height in pixels of this sprite
		 */
		public int getHeight() {
			return image.getHeight(null);
		}

		/**
		 * Draw the sprite onto the graphics context provided
		 *
		 * @param g
		 *            The graphics context on which to draw the sprite
		 * @param x
		 *            The x location at which to draw the sprite
		 * @param y
		 *            The y location at which to draw the sprite
		 */
		public void draw(GraphicsHandler g, int x, int y) {
			g.drawImage(this, x, y);
		}

		public void draw(GraphicsHandler g, int x, int y, Color tint) {
			g.drawImage(this, x, y, tint);
		}

		public void draw(GraphicsHandler g, int x, int y, int width, int height) {
			g.drawImage(this, x, y, width, height);
		}

		public void draw(GraphicsHandler g, int x, int y, int width, int height, Color tint) {
			g.drawImage(this, x, y, width, height, tint);
		}

		/**
		 * Always treat de-serialization as a full-blown constructor, by
		 * validating the final state of the de-serialized object.
		 */
		@Override
		public void readObject(ObjectInputStream aInputStream) throws ClassNotFoundException,
				IOException {
			// always perform the default de-serialization first
			// aInputStream.defaultReadObject();
			ref = (String) aInputStream.readObject();
			this.image = ((AwtSprite) AwtSpriteStore.get().getSprite(ref)).image;
		}

		/**
		 * This is the default implementation of writeObject.
		 * Customise if necessary.
		 */
		@Override
		public void writeObject(ObjectOutputStream aOutputStream) throws IOException {
			// perform the default serialization for all non-transient, non-static fields
			aOutputStream.writeObject(ref);
			aOutputStream.defaultWriteObject();
		}
	}
	
	/** The cached sprite map, from reference to sprite instance */
	private HashMap<String, Sprite> sprites = new HashMap<String, Sprite>();
	
	/**
	 * Retrieve a sprite from the store
	 * 
	 * @param ref
	 *            The reference to the image to use for the sprite
	 * @return A sprite instance containing an accelerate image of the request reference
	 */
	public Sprite getSprite(String ref) {
		// if we've already got the sprite in the cache
		// then just return the existing version
		if (sprites.get(ref) != null) {
			return sprites.get(ref);
		}
		
		// create a sprite, add it the cache then return it
		Sprite sprite = loadSprite(ref);
		sprites.put(ref, sprite);
		
		return sprite;
	}
	
	public abstract Sprite loadSprite(String ref);
}
