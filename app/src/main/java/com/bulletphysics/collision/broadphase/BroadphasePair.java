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

package com.bulletphysics.collision.broadphase;

import java.util.Comparator;

/**
 * BroadphasePair class contains a pair of AABB-overlapping objects.
 * {@link Dispatcher} can search a {@link CollisionAlgorithm} that performs
 * exact/narrowphase collision detection on the actual collision shapes.
 *
 * @author jezek2
 */
public class BroadphasePair {

	public BroadphaseProxy pProxy0;
	public BroadphaseProxy pProxy1;
	public CollisionAlgorithm algorithm;
	public Object userInfo;

	public BroadphasePair() {
	}

	public BroadphasePair(BroadphaseProxy pProxy0, BroadphaseProxy pProxy1) {
		this.pProxy0 = pProxy0;
		this.pProxy1 = pProxy1;
		this.algorithm = null;
		this.userInfo = null;
	}
	
	public void set(com.bulletphysics.collision.broadphase.BroadphasePair p) {
		pProxy0 = p.pProxy0;
		pProxy1 = p.pProxy1;
		algorithm = p.algorithm;
		userInfo = p.userInfo;
	}
	
	public boolean equals(com.bulletphysics.collision.broadphase.BroadphasePair p) {
		return pProxy0 == p.pProxy0 && pProxy1 == p.pProxy1;
	}
	
	public static final Comparator<com.bulletphysics.collision.broadphase.BroadphasePair> broadphasePairSortPredicate = new Comparator<com.bulletphysics.collision.broadphase.BroadphasePair>() {
		@Override
        public int compare(com.bulletphysics.collision.broadphase.BroadphasePair a, com.bulletphysics.collision.broadphase.BroadphasePair b) {
			// JAVA TODO:
			BroadphaseProxy a0 = a.pProxy0;
			BroadphaseProxy b0 = b.pProxy0;
			BroadphaseProxy a1 = a.pProxy1;
			BroadphaseProxy b1 = b.pProxy1;
			boolean result = a0.uid > b0.uid ||
					(a0.uid == b0.uid && a1.uid > b1.uid) ||
					(a0.uid == b0.uid && a1.uid == b1.uid /*&& a.algorithm > b.m_algorithm*/);
			return result? -1 : 1;
		}
	};
	
}
