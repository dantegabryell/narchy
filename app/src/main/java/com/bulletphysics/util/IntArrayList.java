/*
 * Java port of Bullet (c) 2008 Martin Dvorak <jezek2@advel.cz>
 *
 * Bullet Continuous Collision Detection and Physics Library
 * Copyright (c) 2003-2008 Erwin Coumans  http://www.bulletphysics.com/
 *
 * This software is provided 'as-is', without any express or implied warranty.
 * In no event will the authors be held liable for any damages arising from
 * the use of this software.
 * 
 * Permission is granted to anyone to use this software for any purpose, 
 * including commercial applications, and to alter it and redistribute it
 * freely, subject to the following restrictions:
 * 
 * 1. The origin of this software must not be misrepresented; you must not
 *    claim that you wrote the original software. If you use this software
 *    in a product, an acknowledgment in the product documentation would be
 *    appreciated but is not required.
 * 2. Altered source versions must be plainly marked as such, and must not be
 *    misrepresented as being the original software.
 * 3. This notice may not be removed or altered from any source distribution.
 */

package com.bulletphysics.util;

/**
 *
 * @author jezek2
 */
public final class IntArrayList {

	private int[] array = new int[16];
	private int size;
	
	public void add(int value) {
		int[] a = this.array;
		if (size == a.length) {
			expand();
			a = this.array;
		}
		
		a[size++] = value;
	}
	
	private void expand() {
		int[] newArray = new int[array.length << 1];
		System.arraycopy(array, 0, newArray, 0, array.length);
		array = newArray;
	}

	public int remove(int index) {
		int s = this.size;
		if (index >= s) throw new IndexOutOfBoundsException();
		int[] a = this.array;
		int old = a[index];
		if (index!= this.size -1)
			System.arraycopy(a, index+1, a, index, s - index - 1);
		this.size--;
		return old;
	}

	public int get(int index) {
		//if (index >= size) throw new IndexOutOfBoundsException();
		return array[index];
	}

	public void set(int index, int value) {
		//if (index >= size) throw new IndexOutOfBoundsException();
		array[index] = value;
	}

	public int size() {
		return size;
	}
	
	public void clear() {
		size = 0;
	}

}
