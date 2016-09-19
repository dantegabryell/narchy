/*
 * Copyright 2012 Jonathan Leahey
 * 
 * This file is part of Minicraft
 * 
 * Minicraft is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * 
 * Minicraft is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with Minicraft. If not, see http://www.gnu.org/licenses/.
 */

package nars.experiment.minicraft.side;


public class TileType implements java.io.Serializable {
	private static final long serialVersionUID = 1L;
	
	/** The sprite that represents this Type */
	protected Sprite sprite;
	protected TileID name;
	protected boolean passable;
	protected boolean liquid;
	protected int lightBlocking;
	protected int lightEmitting;
	
	public TileType(String ref, TileID name) {
		this(ref, name, false, false, Constants.LIGHT_VALUE_OPAQUE);
	}
	
	public TileType(String ref, TileID name, boolean passable, boolean liquid, int lightBlocking) {
		this(ref, name, passable, liquid, lightBlocking, 0);
	}
	
	public TileType(String ref, TileID name, boolean passable, boolean liquid, int lightBlocking,
			int lightEmitting) {
		this.sprite = SpriteStore.get().getSprite(ref);
		this.name = name;
		this.passable = passable;
		this.liquid = liquid;
		this.lightBlocking = lightBlocking;
		this.lightEmitting = lightEmitting;
	}
	
	public void draw(GraphicsHandler g, int x, int y) {
		sprite.draw(g, x, y);
	}
}
