/* MapDef.java
 * Component: ProperJavaRDP
 *
 * Revision: $Revision: #2 $
 * Author: $Author: tvkelley $
 * Date: $Date: 2009/09/15 $
 *
 * Copyright (c) 2005 Propero Limited
 *
 * Purpose: Encapsulates an individual key mapping
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or (at
 * your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 * USA
 *
 * (See gpl.txt for details of the GNU General Public License.)
 *
 */
package net.propero.rdp.keymapping;

import net.propero.rdp.Constants;
import net.propero.rdp.Options;

import java.awt.event.KeyEvent;
import java.io.PrintStream;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

public class MapDef {

    
    
    private static final int FLAG_SHIFT = 0x01; 

    private static final int FLAG_CTRL = 0x02; 

    private static final int FLAG_ALT = 0x04; 

    private static final int FLAG_CAPSLOCK = 0x08; 

    private final int scancode;

    private final boolean ctrlDown;

    private final boolean shiftDown;

    private final boolean altDown;

    private final boolean capslockDown;
    private final boolean characterDef;
    private final int keyLocation;
    private char keyChar;
    private int keyCode;

    /**
     * Constructor for a character-defined mapping definition
     *
     * @param keyChar
     * @param keyLocation
     * @param scancode
     * @param ctrlDown
     * @param shiftDown
     * @param altDown
     * @param capslockDown
     */
    public MapDef(char keyChar, int keyLocation, int scancode,
                  boolean ctrlDown, boolean shiftDown, boolean altDown,
                  boolean capslockDown) {
        this.keyChar = keyChar;
        this.characterDef = true;

        this.keyLocation = keyLocation;
        this.scancode = scancode;
        this.ctrlDown = ctrlDown;
        this.altDown = altDown;
        this.shiftDown = shiftDown;
        this.capslockDown = capslockDown;
    }

    /**
     * Constructor for a keycode-defined mapping definition
     *
     * @param keyChar
     * @param keyLocation
     * @param scancode
     * @param ctrlDown
     * @param shiftDown
     * @param altDown
     * @param capslockDown
     */
    public MapDef(int keyCode, int keyLocation, int scancode, boolean ctrlDown,
                  boolean shiftDown, boolean altDown, boolean capslockDown) {
        this.keyCode = keyCode;
        this.characterDef = false;

        this.keyLocation = keyLocation;
        this.scancode = scancode;
        this.ctrlDown = ctrlDown;
        this.altDown = altDown;
        this.shiftDown = shiftDown;
        this.capslockDown = capslockDown;
    }

    /**
     * Constructor for a mapping definition based on a given string
     * representation (as would be output to a stream by the writeToStream
     * method).
     *
     * @param definition One-line definition string
     * @throws KeyMapException Any parsing errors which may occur
     */
    public MapDef(String definition) throws KeyMapException {
        StringTokenizer st = new StringTokenizer(definition);
        try {
            
            characterDef = (Integer.parseInt(st.nextToken()) == 1);

            
            if (characterDef)
                keyChar = (char) Integer.parseInt(st.nextToken());
            else
                keyCode = Integer.parseInt(st.nextToken());

            
            keyLocation = Integer.parseInt(st.nextToken());

            
            scancode = Integer.decode(st.nextToken());

            
            int modifiers = Integer.parseInt(st.nextToken());
            shiftDown = ((modifiers & FLAG_SHIFT) != 0);
            ctrlDown = ((modifiers & FLAG_CTRL) != 0);
            altDown = ((modifiers & FLAG_ALT) != 0);
            capslockDown = ((modifiers & FLAG_CAPSLOCK) != 0);

        } catch (NumberFormatException nfEx) {
            throw new KeyMapException(nfEx.getMessage() + " is not numeric");
        } catch (NoSuchElementException nseEx) {
            throw new KeyMapException("Not enough parameters in definition");
        }

    }

    public int getKeyCode() {
        return keyCode;
    }

    public char getKeyChar() {
        return keyChar;
    }

    /**
     * Return the scancode associated with this mapping
     *
     * @return
     */
    public int getScancode() {
        return scancode;
    }

    /**
     * Return true if this mapping is defined by a character, false otherwise
     *
     * @return
     */
    public boolean isCharacterDef() {
        return characterDef;
    }

    /**
     * Return true if the keystroke defined in this mapping requires that the
     * Control key be down
     *
     * @return
     */
    public boolean isCtrlDown() {
        return ctrlDown;
    }

    /**
     * Return true if the keystroke defined in this mapping requires that the
     * Alt key be down
     *
     * @return
     */
    public boolean isAltDown() {
        return altDown;
    }

    /**
     * Return true if the keystroke defined in this mapping requires that the
     * Shift key be down
     *
     * @return
     */
    public boolean isShiftDown() {
        return shiftDown;
    }

    /**
     * Return true if the keystroke defined in this mapping requires that Caps
     * Lock is on
     *
     * @return
     */
    public boolean isCapslockOn() {
        return capslockDown;
    }

    /**
     * Return the number of modifiers that would need to be changed to send the
     * specified character/key using this particular mapping.
     *
     * @param e        Key event which was received by Java
     * @param capslock Is the Caps Lock key down?
     * @return The number of modifier changes to make
     */
    public int modifierDistance(KeyEvent e, boolean capslock) {
        
        

        if (!characterDef)
            return 0;

        int dist = 0;
        if (ctrlDown != e.isControlDown())
            dist += 1;
        if (altDown != e.isAltDown())
            dist += 1;
        if (shiftDown != e.isShiftDown())
            dist += 1;
        if (capslockDown != capslock)
            dist += 1;
        return dist;
    }

    public boolean appliesTo(char c) {
        return ((characterDef) && (this.keyChar == c) && !(capslockDown));
    }

    /**
     * Return true if this map definition applies to the supplied key event
     *
     * @param e KeyEvent to check definition against
     * @return
     */
    protected boolean appliesToTyped(KeyEvent e) {
        return ((characterDef) && (this.keyChar == e.getKeyChar()));
    }

    boolean appliesToTyped(KeyEvent e, boolean capslock) {

        if (Constants.OS == Constants.MAC) {
            
            if (Options.remap_hash && (e.getKeyChar() == '�')) {
                return ((characterDef) && (this.keyChar == '#'));
            }

            
            if (capslock && Character.isLetter(e.getKeyChar())
                    && Character.isUpperCase(e.getKeyChar()) && e.isShiftDown()) {
                char c = Character.toLowerCase(e.getKeyChar());
                return ((characterDef) && (this.keyChar == c));
            }
        }

        return ((characterDef) && (this.keyChar == e.getKeyChar()));
    }

    /**
     * Return true if this map definition applies to the supplied key event
     *
     * @param e KeyEvent to check definition against
     * @return
     */
    boolean appliesToPressed(KeyEvent e) {
        
        if (!characterDef) {
            if (!(!ctrlDown || e.isControlDown()))
                return false;
            if (!(!altDown || e.isAltDown()))
                return false;
        }

        return ((!characterDef) && (this.keyCode == e.getKeyCode()));
    }

    /**
     * Output this mapping definition to a stream, formatted as a single line
     * (characterDef character/keycode location scancode modifiers
     * [description])
     *
     * @param p Stream to write to
     */
    public void writeToStream(PrintStream p) {

        
        
        String definition = String.valueOf(characterDef ? 1 : 0);

        
        definition += "\t";
        if (characterDef)
            definition += keyChar;
        else
            definition += keyCode;

        
        definition += "\t" + keyLocation;
        definition += "\t0x" + Integer.toHexString(scancode);

        
        int modifiers = 0;
        modifiers |= (shiftDown ? FLAG_SHIFT : 0);
        modifiers |= (ctrlDown ? FLAG_CTRL : 0);
        modifiers |= (altDown ? FLAG_ALT : 0);
        modifiers |= (capslockDown ? FLAG_CAPSLOCK : 0);
        definition += "\t" + modifiers;

        
        if (!characterDef)
            definition += '\t' + KeyEvent.getKeyText(this.keyCode);

        
        p.println(definition);
    }

}