/*******************************************************************************
 * Copyright (c) 2013, Daniel Murphy
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 * 	* Redistributions of source code must retain the above copyright notice,
 * 	  this list of conditions and the following disclaimer.
 * 	* Redistributions in binary form must reproduce the above copyright notice,
 * 	  this list of conditions and the following disclaimer in the documentation
 * 	  and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/
package org.jbox2d.dynamics.joints;

import org.jbox2d.common.MathUtils;
import org.jbox2d.common.Rot;
import org.jbox2d.common.Settings;
import org.jbox2d.dynamics.Body2D;
import org.jbox2d.dynamics.SolverData;
import org.jbox2d.pooling.IWorldPool;
import spacegraph.math.Tuple2f;
import spacegraph.math.v2;

//Linear constraint (point-to-line)
//d = pB - pA = xB + rB - xA - rA
//C = dot(ay, d)
//Cdot = dot(d, cross(wA, ay)) + dot(ay, vB + cross(wB, rB) - vA - cross(wA, rA))
//   = -dot(ay, vA) - dot(cross(d + rA, ay), wA) + dot(ay, vB) + dot(cross(rB, ay), vB)
//J = [-ay, -cross(d + rA, ay), ay, cross(rB, ay)]

//Spring linear constraint
//C = dot(ax, d)
//Cdot = = -dot(ax, vA) - dot(cross(d + rA, ax), wA) + dot(ax, vB) + dot(cross(rB, ax), vB)
//J = [-ax -cross(d+rA, ax) ax cross(rB, ax)]

//Motor rotational constraint
//Cdot = wB - wA
//J = [0 0 -1 0 0 1]

/**
 * A wheel joint. This joint provides two degrees of freedom: translation along an axis fixed in
 * bodyA and rotation in the plane. You can use a joint limit to restrict the range of motion and a
 * joint motor to drive the rotation or to model rotational friction. This joint is designed for
 * vehicle suspensions.
 *
 * @author Daniel Murphy
 */
public class WheelJoint extends Joint {

    private float m_frequencyHz;
    private float m_dampingRatio;

    // Solver shared
    private final Tuple2f m_localAnchorA = new v2();
    private final Tuple2f m_localAnchorB = new v2();
    private final Tuple2f m_localXAxisA = new v2();
    private final Tuple2f m_localYAxisA = new v2();

    private float m_impulse;
    private float m_motorImpulse;
    private float m_springImpulse;

    private float m_maxMotorTorque;
    private float m_motorSpeed;
    private boolean m_enableMotor;

    // Solver temp
    private int m_indexA;
    private int m_indexB;
    private final Tuple2f m_localCenterA = new v2();
    private final Tuple2f m_localCenterB = new v2();
    private float m_invMassA;
    private float m_invMassB;
    private float m_invIA;
    private float m_invIB;

    private final Tuple2f m_ax = new v2();
    private final Tuple2f m_ay = new v2();
    private float m_sAx, m_sBx;
    private float m_sAy, m_sBy;

    private float m_mass;
    private float m_motorMass;
    private float m_springMass;

    private float m_bias;
    private float m_gamma;

    protected WheelJoint(IWorldPool argPool, WheelJointDef def) {
        super(argPool, def);
        m_localAnchorA.set(def.localAnchorA);
        m_localAnchorB.set(def.localAnchorB);
        m_localXAxisA.set(def.localAxisA);
        Tuple2f.crossToOutUnsafe(1.0f, m_localXAxisA, m_localYAxisA);


        m_motorMass = 0.0f;
        m_motorImpulse = 0.0f;

        m_maxMotorTorque = def.maxMotorTorque;
        m_motorSpeed = def.motorSpeed;
        m_enableMotor = def.enableMotor;

        m_frequencyHz = def.frequencyHz;
        m_dampingRatio = def.dampingRatio;
    }

    public Tuple2f getLocalAnchorA() {
        return m_localAnchorA;
    }

    public Tuple2f getLocalAnchorB() {
        return m_localAnchorB;
    }

    @Override
    public void getAnchorA(Tuple2f argOut) {
        A.getWorldPointToOut(m_localAnchorA, argOut);
    }

    @Override
    public void getAnchorB(Tuple2f argOut) {
        B.getWorldPointToOut(m_localAnchorB, argOut);
    }

    @Override
    public void getReactionForce(float inv_dt, Tuple2f argOut) {
        final Tuple2f temp = pool.popVec2();
        temp.set(m_ay).scaled(m_impulse);
        argOut.set(m_ax).scaled(m_springImpulse).added(temp).scaled(inv_dt);
        pool.pushVec2(1);
    }

    @Override
    public float getReactionTorque(float inv_dt) {
        return inv_dt * m_motorImpulse;
    }

    public float getJointTranslation() {
        Body2D b1 = A;
        Body2D b2 = B;

        Tuple2f p1 = pool.popVec2();
        Tuple2f p2 = pool.popVec2();
        Tuple2f axis = pool.popVec2();
        b1.getWorldPointToOut(m_localAnchorA, p1);
        b2.getWorldPointToOut(m_localAnchorA, p2);
        p2.subbed(p1);
        b1.getWorldVectorToOut(m_localXAxisA, axis);

        float translation = Tuple2f.dot(p2, axis);
        pool.pushVec2(3);
        return translation;
    }

    /**
     * For serialization
     */
    public Tuple2f getLocalAxisA() {
        return m_localXAxisA;
    }

    public float getJointSpeed() {
        return A.velAngular - B.velAngular;
    }

    public boolean isMotorEnabled() {
        return m_enableMotor;
    }

    public void enableMotor(boolean flag) {
        A.setAwake(true);
        B.setAwake(true);
        m_enableMotor = flag;
    }

    public void setMotorSpeed(float speed) {
        A.setAwake(true);
        B.setAwake(true);
        m_motorSpeed = speed;
    }

    public float getMotorSpeed() {
        return m_motorSpeed;
    }

    public float getMaxMotorTorque() {
        return m_maxMotorTorque;
    }

    public void setMaxMotorTorque(float torque) {
        A.setAwake(true);
        B.setAwake(true);
        m_maxMotorTorque = torque;
    }

    public float getMotorTorque(float inv_dt) {
        return m_motorImpulse * inv_dt;
    }

    public void setSpringFrequencyHz(float hz) {
        m_frequencyHz = hz;
    }

    public float getSpringFrequencyHz() {
        return m_frequencyHz;
    }

    public void setSpringDampingRatio(float ratio) {
        m_dampingRatio = ratio;
    }

    public float getSpringDampingRatio() {
        return m_dampingRatio;
    }

    // pooling
    private final Tuple2f rA = new v2();
    private final Tuple2f rB = new v2();
    private final Tuple2f d = new v2();

    @Override
    public void initVelocityConstraints(SolverData data) {
        m_indexA = A.island;
        m_indexB = B.island;
        m_localCenterA.set(A.sweep.localCenter);
        m_localCenterB.set(B.sweep.localCenter);
        m_invMassA = A.m_invMass;
        m_invMassB = B.m_invMass;
        m_invIA = A.m_invI;
        m_invIB = B.m_invI;

        float mA = m_invMassA, mB = m_invMassB;
        float iA = m_invIA, iB = m_invIB;

        Tuple2f cA = data.positions[m_indexA];
        float aA = data.positions[m_indexA].a;
        Tuple2f vA = data.velocities[m_indexA];
        float wA = data.velocities[m_indexA].w;

        Tuple2f cB = data.positions[m_indexB];
        float aB = data.positions[m_indexB].a;
        Tuple2f vB = data.velocities[m_indexB];
        float wB = data.velocities[m_indexB].w;

        final Rot qA = pool.popRot();
        final Rot qB = pool.popRot();
        final Tuple2f temp = pool.popVec2();

        qA.set(aA);
        qB.set(aB);

        // Compute the effective masses.
        Rot.mulToOutUnsafe(qA, temp.set(m_localAnchorA).subbed(m_localCenterA), rA);
        Rot.mulToOutUnsafe(qB, temp.set(m_localAnchorB).subbed(m_localCenterB), rB);
        d.set(cB).added(rB).subbed(cA).subbed(rA);

        // Point to line constraint
        {
            Rot.mulToOut(qA, m_localYAxisA, m_ay);
            m_sAy = Tuple2f.cross(temp.set(d).added(rA), m_ay);
            m_sBy = Tuple2f.cross(rB, m_ay);

            m_mass = mA + mB + iA * m_sAy * m_sAy + iB * m_sBy * m_sBy;

            if (m_mass > 0.0f) {
                m_mass = 1.0f / m_mass;
            }
        }

        // Spring constraint
        m_springMass = 0.0f;
        m_bias = 0.0f;
        m_gamma = 0.0f;
        if (m_frequencyHz > 0.0f) {
            Rot.mulToOut(qA, m_localXAxisA, m_ax);
            m_sAx = Tuple2f.cross(temp.set(d).added(rA), m_ax);
            m_sBx = Tuple2f.cross(rB, m_ax);

            float invMass = mA + mB + iA * m_sAx * m_sAx + iB * m_sBx * m_sBx;

            if (invMass > 0.0f) {
                m_springMass = 1.0f / invMass;

                float C = Tuple2f.dot(d, m_ax);

                // Frequency
                float omega = 2.0f * MathUtils.PI * m_frequencyHz;

                // Damping coefficient
                float d = 2.0f * m_springMass * m_dampingRatio * omega;

                // Spring stiffness
                float k = m_springMass * omega * omega;

                // magic formulas
                float h = data.step.dt;
                m_gamma = h * (d + h * k);
                if (m_gamma > 0.0f) {
                    m_gamma = 1.0f / m_gamma;
                }

                m_bias = C * h * k * m_gamma;

                m_springMass = invMass + m_gamma;
                if (m_springMass > 0.0f) {
                    m_springMass = 1.0f / m_springMass;
                }
            }
        } else {
            m_springImpulse = 0.0f;
        }

        // Rotational motor
        if (m_enableMotor) {
            m_motorMass = iA + iB;
            if (m_motorMass > 0.0f) {
                m_motorMass = 1.0f / m_motorMass;
            }
        } else {
            m_motorMass = 0.0f;
            m_motorImpulse = 0.0f;
        }

        if (data.step.warmStarting) {
            final Tuple2f P = pool.popVec2();
            // Account for variable time step.
            m_impulse *= data.step.dtRatio;
            m_springImpulse *= data.step.dtRatio;
            m_motorImpulse *= data.step.dtRatio;

            P.x = m_impulse * m_ay.x + m_springImpulse * m_ax.x;
            P.y = m_impulse * m_ay.y + m_springImpulse * m_ax.y;
            float LA = m_impulse * m_sAy + m_springImpulse * m_sAx + m_motorImpulse;
            float LB = m_impulse * m_sBy + m_springImpulse * m_sBx + m_motorImpulse;

            vA.x -= m_invMassA * P.x;
            vA.y -= m_invMassA * P.y;
            wA -= m_invIA * LA;

            vB.x += m_invMassB * P.x;
            vB.y += m_invMassB * P.y;
            wB += m_invIB * LB;
            pool.pushVec2(1);
        } else {
            m_impulse = 0.0f;
            m_springImpulse = 0.0f;
            m_motorImpulse = 0.0f;
        }
        pool.pushRot(2);
        pool.pushVec2(1);

        // data.velocities[m_indexA].v = vA;
        data.velocities[m_indexA].w = wA;
        // data.velocities[m_indexB].v = vB;
        data.velocities[m_indexB].w = wB;
    }

    @Override
    public void solveVelocityConstraints(SolverData data) {
        float mA = m_invMassA, mB = m_invMassB;
        float iA = m_invIA, iB = m_invIB;

        Tuple2f vA = data.velocities[m_indexA];
        float wA = data.velocities[m_indexA].w;
        Tuple2f vB = data.velocities[m_indexB];
        float wB = data.velocities[m_indexB].w;

        final Tuple2f temp = pool.popVec2();
        final Tuple2f P = pool.popVec2();

        // Solve spring constraint
        {
            float Cdot = Tuple2f.dot(m_ax, temp.set(vB).subbed(vA)) + m_sBx * wB - m_sAx * wA;
            float impulse = -m_springMass * (Cdot + m_bias + m_gamma * m_springImpulse);
            m_springImpulse += impulse;

            P.x = impulse * m_ax.x;
            P.y = impulse * m_ax.y;
            float LA = impulse * m_sAx;
            float LB = impulse * m_sBx;

            vA.x -= mA * P.x;
            vA.y -= mA * P.y;
            wA -= iA * LA;

            vB.x += mB * P.x;
            vB.y += mB * P.y;
            wB += iB * LB;
        }

        // Solve rotational motor constraint
        {
            float Cdot = wB - wA - m_motorSpeed;
            float impulse = -m_motorMass * Cdot;

            float oldImpulse = m_motorImpulse;
            float maxImpulse = data.step.dt * m_maxMotorTorque;
            m_motorImpulse = MathUtils.clamp(m_motorImpulse + impulse, -maxImpulse, maxImpulse);
            impulse = m_motorImpulse - oldImpulse;

            wA -= iA * impulse;
            wB += iB * impulse;
        }

        // Solve point to line constraint
        {
            float Cdot = Tuple2f.dot(m_ay, temp.set(vB).subbed(vA)) + m_sBy * wB - m_sAy * wA;
            float impulse = -m_mass * Cdot;
            m_impulse += impulse;

            P.x = impulse * m_ay.x;
            P.y = impulse * m_ay.y;
            float LA = impulse * m_sAy;
            float LB = impulse * m_sBy;

            vA.x -= mA * P.x;
            vA.y -= mA * P.y;
            wA -= iA * LA;

            vB.x += mB * P.x;
            vB.y += mB * P.y;
            wB += iB * LB;
        }
        pool.pushVec2(2);

        // data.velocities[m_indexA].v = vA;
        data.velocities[m_indexA].w = wA;
        // data.velocities[m_indexB].v = vB;
        data.velocities[m_indexB].w = wB;
    }

    @Override
    public boolean solvePositionConstraints(SolverData data) {
        Tuple2f cA = data.positions[m_indexA];
        float aA = data.positions[m_indexA].a;
        Tuple2f cB = data.positions[m_indexB];
        float aB = data.positions[m_indexB].a;

        final Rot qA = pool.popRot();
        final Rot qB = pool.popRot();
        final Tuple2f temp = pool.popVec2();

        qA.set(aA);
        qB.set(aB);

        Rot.mulToOut(qA, temp.set(m_localAnchorA).subbed(m_localCenterA), rA);
        Rot.mulToOut(qB, temp.set(m_localAnchorB).subbed(m_localCenterB), rB);
        d.set(cB).subbed(cA).added(rB).subbed(rA);

        Tuple2f ay = pool.popVec2();
        Rot.mulToOut(qA, m_localYAxisA, ay);

        float sAy = Tuple2f.cross(temp.set(d).added(rA), ay);
        float sBy = Tuple2f.cross(rB, ay);

        float C = Tuple2f.dot(d, ay);

        float k = m_invMassA + m_invMassB + m_invIA * m_sAy * m_sAy + m_invIB * m_sBy * m_sBy;

        float impulse;
        if (k != 0.0f) {
            impulse = -C / k;
        } else {
            impulse = 0.0f;
        }

        final Tuple2f P = pool.popVec2();
        P.x = impulse * ay.x;
        P.y = impulse * ay.y;
        float LA = impulse * sAy;
        float LB = impulse * sBy;

        cA.x -= m_invMassA * P.x;
        cA.y -= m_invMassA * P.y;
        aA -= m_invIA * LA;
        cB.x += m_invMassB * P.x;
        cB.y += m_invMassB * P.y;
        aB += m_invIB * LB;

        pool.pushVec2(3);
        pool.pushRot(2);
        // data.positions[m_indexA].c = cA;
        data.positions[m_indexA].a = aA;
        // data.positions[m_indexB].c = cB;
        data.positions[m_indexB].a = aB;

        return Math.abs(C) <= Settings.linearSlop;
    }
}
