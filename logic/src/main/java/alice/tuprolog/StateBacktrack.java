/*
 * tuProlog - Copyright (C) 2001-2002  aliCE team at deis.unibo.it
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package alice.tuprolog;

import alice.util.OneWayList;

import java.util.List;


/**
 * @author Alex Benini
 *
 */
public class StateBacktrack extends State {
    

    
    public StateBacktrack(EngineRunner c) {
        this.c = c;
        stateName = "Back";
    }
    
    
    /* (non-Javadoc)
     * @see alice.tuprolog.AbstractRunState#doJob()
     */
    @Override
    void run(Engine e) {
        ChoicePointContext curChoice = e.choicePointSelector.fetch();
        
        if (curChoice == null) {
            e.nextState = c.END_FALSE;
            
            
            
            
            return;
        }
        e.currentAlternative = curChoice;
        
        
        e.currentContext = curChoice.executionContext;
        Term curGoal = e.currentContext.goalsToEval.backTo(curChoice.indexSubGoal).term();
        if (!(curGoal instanceof Struct)) {
            e.nextState = c.END_FALSE;
            return;
        }
        e.currentContext.currentGoal = (Struct) curGoal;
        
        
        
        ExecutionContext curCtx = e.currentContext;
        OneWayList<List<Var>> pointer = curCtx.trailingVars;
        OneWayList<List<Var>> stopDeunify = curChoice.varsToDeunify;
        List<Var> varsToDeunify = stopDeunify.head;
        Var.free(varsToDeunify);
        varsToDeunify.clear();
        
        do {
            
            while (pointer != stopDeunify) {
                Var.free(pointer.head);
                pointer = pointer.tail;
            }
            curCtx.trailingVars = pointer;
            if (curCtx.fatherCtx == null)
                break;
            stopDeunify = curCtx.fatherVarsList;
            SubGoal fatherIndex = curCtx.fatherGoalId;

            Term prevGoal = curGoal;
            curCtx = curCtx.fatherCtx;
            curGoal = curCtx.goalsToEval.backTo(fatherIndex).term();
            if (!(curGoal instanceof Struct) || prevGoal == curGoal) {
                e.nextState = c.END_FALSE;
                return;
            }
            curCtx.currentGoal = (Struct)curGoal;
            pointer = curCtx.trailingVars;
        } while (true);
        
        
        e.nextState = c.GOAL_EVALUATION;
    }
    
}