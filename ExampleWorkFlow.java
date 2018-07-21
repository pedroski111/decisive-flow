package com.fastjet.workflow.examples;

import com.fastjet.workflow.DecisiveFlow;
import com.fastjet.workflow.DecisiveFlow.Builder;
import com.fastjet.workflow.annotations.ActionNode;
import com.fastjet.workflow.annotations.DecisionNode;
import com.fastjet.workflow.annotations.FinalNode;
import com.fastjet.workflow.annotations.Parameter;

/**
 * The Class Example.
 * 
 * A simple example of how to use DecisiveFlow.
 * 
 * Notice how the work-flow is encapsulated in a single class.
 */
public class ExampleWorkFlow {

	public static void main(String[] args) {
		ExampleWorkFlow workFlow = new ExampleWorkFlow();
		workFlow.start("a", 5);
	}
	
	/**
	 * The Enum Result.
	 * 
	 * Possible outcomes of the work-flow
	 */
	public enum Result{
		RESULT_X, RESULT_Y, RESULT_Z;
	}
	
	public void start(String a, Integer b){
		DecisiveFlow<Result, ExampleWorkFlow> p = new DecisiveFlow.Builder<Result, ExampleWorkFlow>(this)
				.debug(true)
				.addDecisionNode("A").trueOutcome("B").falseOutcome("G").commit()
				.addDecisionNode("B").trueOutcome("C").falseOutcome("E").commit()
				.addDecisionNode("G").trueOutcome("E").falseOutcome("F").commit()
				.addActionNode("C").outcome("D").commit()
				.build(a, b); //Add arguments
		Result r = p.getResult();
		System.out.println(r);
	}
	
	@DecisionNode("A")
	public boolean taskA(@Parameter(0) String a){
		return a=="a"?true:false;
	}
	
	@DecisionNode({"B","G"})
	public boolean taskB(@Parameter(1) int b){ 
		return b==5?true:false;
	}
	
	@ActionNode("C")
	public void taskC(){
		System.out.println("C");
	}
	
	@FinalNode("D")
	public Result taskE(){  
		return Result.RESULT_X;
	}
	
	@FinalNode("E")
	public Result taskF(){
		return Result.RESULT_Y;
	}
	
	@FinalNode("F")
	public Result taskD(){
		return Result.RESULT_Z;
	}
}
