/*

   Derby - Class org.apache.derby.iapi.store.raw.Compensation

   Copyright 1997, 2004 The Apache Software Foundation or its licensors, as applicable.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

 */

package org.apache.derby.iapi.store.raw;

/**
	A Compensation operation can compensate for the action of a log operation.
	A Compensation operation itself is not undo-able, i.e., it is loggable but
	not undoable.

	A Compensation operation is generated by the logging system when it calls
	undoable.generateUndo().  GenerateUndo should be the <B>only</B> way a 
	compensation operation can be made.

	@see Undoable#generateUndo
*/
public interface Compensation extends Loggable {

	/**
	  Set up the undoable operation during recovery redo.

	  @param op the Undoable operation
	  @see Loggable#needsRedo
	*/
	public void setUndoOp(Undoable op);
}
