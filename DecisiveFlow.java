package com.fastjet.workflow;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

import com.fastjet.workflow.annotations.ActionNode;
import com.fastjet.workflow.annotations.DecisionNode;
import com.fastjet.workflow.annotations.FinalNode;
import com.fastjet.workflow.annotations.Parameter;


/**
 * The Class DecisiveFlow.
 *
 * A simple work-flow designer that is configured using a fluent interface.
 * 
 * Based upon binary decision tree logic.
 * 
 * Three possible nodes, which are annotated on methods only:
 * 1. Action - A simple pass through node that perform an action using specified parameters. Returns VOID.
 * 2. Decision - Self explanatory. May use specified parameters to make decision. Returns BOOLEAN.
 * 3. Final - Can be more than one. Output is always an ENUM. Return ENUM. 
 * 
 * When defining the work-flow, all nodes must specify an ID. Each ID must be unique.
 * Incorrect work-flows will automatically be validated and rejected with Exceptions.
 * 
 * Limitations: 
 * 	A work-flow is encapsulated in a single class and cannot be spread out over many classes.
 *  Future designs will allow nested work-flows; the ability to call others.
 *  
 *
 * @author Paul Pedri @ FastJet Software service@fast-jet.com.au
 * @param <E> Enum return state of work-flow
 * @param <T> Class type containing work-flow logic methods.
 */
public class DecisiveFlow<E extends Enum<?>, T>{

	private Map<String, Outcome> nodes;
	private List<Object> parameters;
	private String startNodeId;
	private T activityObject;
	private boolean debug = false;

	public DecisiveFlow(T objectFlowLogic){
		this.activityObject = objectFlowLogic; 
		parameters = new ArrayList<Object>();
		nodes = new HashMap<String, Outcome>();
	}

	private void addDecisionNode(String currentNodeId, String nextTrueNodeId, String nextFalseNodeId){

		nodes.put(currentNodeId, new DecisionOutcome(nextTrueNodeId, nextFalseNodeId));
	}

	private void addActionNode(String currentNodeId, String nextNodeId){
		nodes.put(currentNodeId, new ActionOutcome(nextNodeId));
	}

	private void setParameters(Object... o){ //has to be object
		for(Object p:o){
			parameters.add(p);
		}
	}

	/**
	 * Validate process and find the start node
	 * 
	 */
	private void validateProcess(){

		//find non-unique nodeids in graph
		Set<String> nodeIds = new HashSet<String>();
		try{
			for(String t:nodes.keySet()){
				nodeIds.add(t);
			}
			for(Outcome d:nodes.values()){
				d.addOutcomesToSet(nodeIds);;
			}
		}
		catch(Exception e){
			throw new IllegalStateException("Node IDs in graph are not unique.");
		}

		//min size
		if(nodeIds.size()<3)
			throw new IllegalStateException("Must have at least 3 nodes in decision tree graph; a decision and two end node.");

		//find start node id
		Map<String, Boolean> candidatesForStartId = new HashMap<String, Boolean>();
		for(String t:nodes.keySet()){
			candidatesForStartId.put(t, true);
		}

		for(String id:candidatesForStartId.keySet()){
			for(Outcome outcome:nodes.values()){
				outcome.addCandidatesForStartId(candidatesForStartId, id);
			}
		} 
		int count = 0;
		for(String id:candidatesForStartId.keySet()){
			if(candidatesForStartId.get(id)==true){
				count++;
				startNodeId = id;
			}
		}
		if(count>1)
			throw new IllegalStateException("More than one start node ID has been identified. Make sure graph of node IDs are linked.");
		else if(count==0)
			throw new IllegalStateException("Circular node list identified. Could not determine start node ID from graph specified.");

		//Start node id
		if(!nodeIds.contains(startNodeId))
			throw new IllegalStateException("The start node ID is invalid. It must match the graph of supplied node IDs.");

		//match node id in graph to methods
		Method[] methods = activityObject.getClass().getMethods();
		for(Method m:methods){

			if(m.getAnnotation(DecisionNode.class)!=null)
			{
				for(String id:m.getAnnotation(DecisionNode.class).value()){
					if(!nodeIds.contains(id))
						throw new IllegalStateException("Method "+ m.getName()+" with @DecisionNode annotation does not exist in the node tree graph using ID : "+ id);
				}
			}
			else if(m.getAnnotation(ActionNode.class)!=null)
			{
				if(!nodeIds.contains(m.getAnnotation(ActionNode.class).value()))
					throw new IllegalStateException("Method "+ m.getName()+" with @ActionNode annotation does not exist in the node tree graph using ID : "+ m.getAnnotation(ActionNode.class).value());

			}
		}

		//ensure correct signatures
		for(Method m:methods){

			if(m.getAnnotation(DecisionNode.class)!=null 
					&& !m.getReturnType().getName().equals("boolean")){ 
				throw new IllegalStateException("@DecisionNode annotation on method \""+ m.getName() +"\" must return BOOLEAN.");
			}
			if(m.getAnnotation(FinalNode.class)!=null && !m.getReturnType().isEnum()){ 
				throw new IllegalStateException("@FinalNode annotation on method \""+ m.getName() +"\" must return ENUM.");
			} 
			if(m.getAnnotation(FinalNode.class)!=null && m.getParameterTypes().length!=0){
				throw new IllegalStateException("@FinalNode annotation on method \""+ m.getName() +"\" must have no parameters.");
			}
			if(m.getAnnotation(ActionNode.class)!=null &&
					!m.getReturnType().equals(Void.TYPE)){
				throw new IllegalStateException("@ActionNode annotation on method \""+ m.getName() +"\" must return VOID.");
			}

			//check parameters on activity methods have @Parameter annotation
			if(m.getAnnotation(ActionNode.class)!=null ||
					m.getAnnotation(FinalNode.class)!=null || 
					m.getAnnotation(DecisionNode.class)!=null
					){
				Annotation[][] a = m.getParameterAnnotations();
				boolean broken = false;
				for(int i=0;i<a.length;i++){
					if(a[i].length==0)
						broken = true;

					for(int j=0;j<a[i].length;j++){
						//iterate through annotation per parameter
						if(a[i][j].annotationType() == Parameter.class){
							break;
						}

						//broken if last iteration doesn't have @Parameter
						if(j==a[i].length-1)
							broken = true;
					}	
				}	

				if(broken)
					throw new IllegalStateException("@FinalNode, @ActionNode or @DecisionNode method '"+m.getName()+"' must be annotated with @Parameter");

			}
		}

		//ensure graph is fully linked
		//doesn't have an endnode if a true or false
		//graph is detached
		Set<String> visitedNodeIds = new HashSet<String>();

		int result = checkNode(startNodeId, 0, visitedNodeIds);
		if(debug) {
			System.out.println("Nodes in graph: " + result);
			System.out.println("Size of graph: " + nodeIds.size());
		}

		if(result!=nodeIds.size())
			throw new IllegalStateException("Ensure all nodes are linked to either @FinalNode or @DecisionNode. Decsion tree graph broken.");
	}

	private void printVisitedNodes(Set<String> visitedNodeIds){
		for(String s:visitedNodeIds){
			System.out.print(s+" ");
		}
		System.out.println();
	}

	private int checkNode(String nodeId, int count, Set<String> visitedNodeIds){
		if(isNodeADecisionNodeInGraph(nodeId)){
			if(!doesNodeHaveDecisionNodeAnnotation(nodeId))
				throw new IllegalStateException("Node ID in graph must have a @DecisonNode annotated method for node ID \""+ nodeId +"\" as defined in decision tree graph.");

			DecisionOutcome de = (DecisionOutcome)nodes.get(nodeId);

			checkCompleteDecisionOutcome(de);

			count = checkNode(de.trueOutcomeNodeId, count, visitedNodeIds);
			count = checkNode(de.falseOutcomeNodeId, count, visitedNodeIds);

			//if(debug){
			//System.out.println("Visited Nodes: " + nodeId);

			//printVisitedNodes(visitedNodeIds); //test
			//}

			if(!visitedNodeIds.contains(nodeId)){
				visitedNodeIds.add(nodeId);
				return count + 1;
			}
			else
				return count;
		}
		else if(isNodeAnActionNodeInGraph(nodeId)){
			if(!doesNodeHaveActionNodeAnnotation(nodeId))
				throw new IllegalStateException("Node ID in graph must have a @ActionNode annotated method for node ID \""+ nodeId +"\" as defined in decision tree graph.");

			checkCompleteActionOutcome((ActionOutcome)nodes.get(nodeId));

			ActionOutcome ao = (ActionOutcome)nodes.get(nodeId);

			checkCompleteActionOutcome(ao);

			count = checkNode(ao.outcomeNodeId, count, visitedNodeIds); 

			//if(debug)printVisitedNodes(visitedNodeIds); //test

			if(!visitedNodeIds.contains(nodeId)){
				visitedNodeIds.add(nodeId);
				return count + 1;
			}
			else
				return count;

		}
		else{  //must be an end node
			if(!doesNodeHaveFinalNodeAnnotation(nodeId))
				throw new IllegalStateException("Node ID in graph must have an @FinalNode annotated method for node ID \""+ nodeId +"\" as defined in decision tree graph.");
			else{
				//if(debug)printVisitedNodes(visitedNodeIds); //test

				if(!visitedNodeIds.contains(nodeId)){
					visitedNodeIds.add(nodeId);
					return count + 1;
				}
				else
					return count;
			}
		}
	}

	//check completeness of outcome

	private void checkCompleteDecisionOutcome(DecisionOutcome decisionOutcome){
		if(decisionOutcome.falseOutcomeNodeId==null || decisionOutcome.falseOutcomeNodeId.isEmpty()){
			throw new IllegalStateException("Graph defined decision node must have a FALSE outcome node specified. Not NULL or EMPTY.");
		}

		if(decisionOutcome.trueOutcomeNodeId==null || decisionOutcome.trueOutcomeNodeId.isEmpty()){
			throw new IllegalStateException("Graph defined decision node must have a TRUE outcome node specified. Not NULL or EMPTY.");
		}
	}

	private void checkCompleteActionOutcome(ActionOutcome decisionOutcome){
		if(decisionOutcome.outcomeNodeId==null || decisionOutcome.outcomeNodeId.isEmpty()){
			throw new IllegalStateException("Graph defined action must have an outcome node specified. Not NULL or EMPTY.");
		}

	}

	//what kind of node is it?
	private boolean isNodeADecisionNodeInGraph(String nodeId){
		if(nodes.containsKey(nodeId) && nodes.get(nodeId).getClass()==DecisionOutcome.class)
			return true;
		else 
			return false;
	}

	private boolean isNodeAnActionNodeInGraph(String nodeId){
		if(nodes.containsKey(nodeId) && nodes.get(nodeId).getClass()==ActionOutcome.class)
			return true;
		else 
			return false;
	}

	//check annotations has corresponding node in graph
	private boolean doesNodeHaveFinalNodeAnnotation(String nodeId){
		Method[] methods = activityObject.getClass().getMethods();
		for(Method m:methods){

			if(m.getAnnotation(FinalNode.class)!=null 
					&& m.getAnnotation(FinalNode.class).value().equals(nodeId)){
				return true;
			}
		}
		return false;
	}

	private boolean doesNodeHaveDecisionNodeAnnotation(String nodeId){
		Method[] methods = activityObject.getClass().getMethods();
		for(Method m:methods){

			if(m.getAnnotation(DecisionNode.class)!=null)
			{
				for(String id: m.getAnnotation(DecisionNode.class).value()){
					if(id.equals(nodeId)){
						return true;
					}
				} 
			}
		}
		return false;
	}

	private boolean doesNodeHaveActionNodeAnnotation(String nodeId){
		Method[] methods = activityObject.getClass().getMethods();
		for(Method m:methods){

			if(m.getAnnotation(ActionNode.class)!=null 
					&& m.getAnnotation(ActionNode.class).value().equals(nodeId)){
				return true;
			}
		}
		return false;
	}

	//output
	public E getResult(){

		try {
			if(debug) System.out.println("Start Node: " + startNodeId);
			return doNode(startNodeId);
		} catch (InvocationTargetException e) { 
			e.printStackTrace();
		} catch (IllegalAccessException e) { 
			e.printStackTrace();
		}

		return null;
	}

	/**
	 * Do node. RECURSIVE
	 *
	 * @param nodeId the node id
	 * @return the e
	 * @throws InvocationTargetException the invocation target exception
	 * @throws IllegalAccessException the illegal access exception
	 */
	private E doNode(String nodeId) throws InvocationTargetException, IllegalAccessException{ 
		//if(debug)System.out.println("do node: " + nodeId);

		if(nodes.get(nodeId)!=null){
			for(Method m:activityObject.getClass().getMethods()){

				if(m.getAnnotation(DecisionNode.class)!=null){

					for(String id: m.getAnnotation(DecisionNode.class).value()){
						if(id.equals(nodeId)){
							DecisionOutcome o = (DecisionOutcome)nodes.get(nodeId);

							if(debug) System.out.println("Decision Node: " + nodeId+", "+m.getName());

							if(executeDecisionNode(m, activityObject)){
								if(debug) System.out.println("Outcome: <True>");

								return doNode(o.trueOutcomeNodeId);
							}

							if(debug) System.out.println("Outcome: <False>");

							return doNode(o.falseOutcomeNodeId);
						}
					}
				}
				else if(m.getAnnotation(ActionNode.class)!=null && m.getAnnotation(ActionNode.class).value().equals(nodeId)){
					ActionOutcome o = (ActionOutcome)nodes.get(nodeId);

					if(debug) System.out.println("Action Node: " + nodeId +", "+m.getName());

					executeActionNode(m, activityObject);

					return doNode(o.outcomeNodeId);
				}
			}
			throw new IllegalStateException("Could not find an action or decison node method annotated with that node ID: "+ nodeId+".");
		}
		else{
			//end node
			for(Method m:activityObject.getClass().getMethods()){
				if(m.getAnnotation(FinalNode.class)==null)
					continue; 

				if(m.getAnnotation(FinalNode.class).value().equals(nodeId)){
					if(debug) System.out.println("End Node: " + nodeId+", "+m.getName());

					return executeEndNode(m, activityObject);
				}
			}
			throw new IllegalStateException("Could not find a final node method annotated with that node ID: " + nodeId+".");
		}
	}

	private E executeEndNode(Method m, Object o)throws InvocationTargetException, IllegalAccessException{
		E res= (E) m.invoke(o);
		if(debug) System.out.println("Result: " + res);
		return res;
	}

	private Boolean executeDecisionNode(Method m, Object o) throws InvocationTargetException, IllegalAccessException{
		List<Object> argsList = new ArrayList<Object>();
		Annotation[][] a = m.getParameterAnnotations();
		for(int i=0;i<a.length;i++){
			//iterate through parameters
			for(int j=0;j<a[i].length ;j++){
				//iterate through annotation per parameter
				if(a[i][j].annotationType() == Parameter.class){
					int index = ((Parameter)a[i][j]).value();
					argsList.add(parameters.get(index));
				}
			}
		}
		return (Boolean)m.invoke(o, argsList.toArray());
	}

	private void executeActionNode(Method m, Object o) throws InvocationTargetException, IllegalAccessException{
		List<Object> argsList = new ArrayList<Object>();
		Annotation[][] a = m.getParameterAnnotations();
		for(int i=0;i<a.length;i++){
			//iterate through parameters
			for(int j=0;j<a[i].length ;j++){
				//iterate through annotation per parameter
				if(a[i][j].annotationType()==Parameter.class){
					int index = ((Parameter)a[i][j]).value();
					argsList.add(parameters.get(index));
				}
			}
		} 
		m.invoke(o, argsList.toArray());
	}

	public static class ActivityDecisionNodeBuilder<E extends Enum<?>, T>{
		private String nextTrueNodeId;
		private String nextFalseNodeId; 
		private Builder<E,T> pb;

		public ActivityDecisionNodeBuilder(Builder<E,T> pb){
			this.pb = pb;
		} 

		public ActivityDecisionNodeBuilder<E,T> trueOutcome(String nextTrueNodeId)	      {
			this.nextTrueNodeId = nextTrueNodeId;
			return this;
		}

		public ActivityDecisionNodeBuilder<E,T> falseOutcome(String nextFalseNodeId)	      {
			this.nextFalseNodeId = nextFalseNodeId;
			return this;
		}

		public Builder<E,T> commit(){
			pb.activity.addDecisionNode(pb.currentNodeId, nextTrueNodeId, nextFalseNodeId); 
			return pb;
		}
	}

	public static class ActivityActionNodeBuilder<E extends Enum<?>, T>{
		private String nextNodeId; 
		private Builder<E,T> pb;

		public ActivityActionNodeBuilder(Builder<E,T> pb){
			this.pb = pb;
		} 

		public ActivityActionNodeBuilder<E,T> outcome(String nextNodeId)	      {
			this.nextNodeId = nextNodeId;
			return this;
		}


		public Builder<E,T> commit(){
			pb.activity.addActionNode(pb.currentNodeId, nextNodeId); 
			return pb;
		}
	}

	public static class Builder<E extends Enum<?>, T>{

		private DecisiveFlow<E, T> activity;
		private String currentNodeId;

		public Builder(T workflowobject){
			activity = new DecisiveFlow<E,T>(workflowobject);
		}

		public ActivityDecisionNodeBuilder<E,T> addDecisionNode(String currentNodeId){
			this.currentNodeId = currentNodeId;
			return new ActivityDecisionNodeBuilder<E,T>(this);
		}

		public ActivityActionNodeBuilder<E, T> addActionNode(String currentNodeId){
			this.currentNodeId = currentNodeId;
			return new ActivityActionNodeBuilder<E,T>(this);
		}

		public DecisiveFlow<E,T> build(Object... activityParameters){
			activity.setParameters(activityParameters);
			activity.validateProcess();
			return activity;
		}

		/**
		 * Debug.
		 * 
		 * Without this. debug output is false
		 *
		 * @param db the true if debug output required, otherwise set false
		 * @return the builder
		 */
		public Builder<E, T> debug(boolean db){
			activity.debug=db;
			return this;
		}
	}

	private static class DecisionOutcome extends Outcome{
		private String trueOutcomeNodeId = null;
		private String falseOutcomeNodeId = null;

		public DecisionOutcome(String trueOutcomeNodeId, String falseOutcomeNodeId) {
			super();
			this.trueOutcomeNodeId = trueOutcomeNodeId;
			this.falseOutcomeNodeId = falseOutcomeNodeId;
			if(this.trueOutcomeNodeId==null || this.falseOutcomeNodeId==null)
				throw new IllegalStateException("A decision outcome must have a true AND false outcome.");
		}

		@Override
		public void addOutcomesToSet(Set<String> nodeIds) {
			nodeIds.add(trueOutcomeNodeId);
			nodeIds.add(falseOutcomeNodeId);
		}

		@Override
		public void addCandidatesForStartId(Map<String, Boolean> candidatesForStartId, String id) {
			if(falseOutcomeNodeId.equals(id)){
				candidatesForStartId.put(id, false);
			}
			if(trueOutcomeNodeId.equals(id)){
				candidatesForStartId.put(id, false);
			}

		}

	}

	private abstract static class Outcome{

		/**
		 * Adds the outcomes to set.
		 * 
		 * Not idempotent. Only call once. Updates not list.
		 *
		 * @param nodeIds the node ids
		 */
		public abstract void addOutcomesToSet(Set<String> nodeIds);

		public abstract void addCandidatesForStartId(Map<String, Boolean> candidatesForStartId, String id);

	}

	private static class ActionOutcome extends Outcome{
		private String outcomeNodeId;

		public ActionOutcome(String outcomeNodeId){
			super();
			this.outcomeNodeId = outcomeNodeId;
			if(outcomeNodeId==null)
				throw new IllegalStateException("An action outcome must have an node id specified");
		}

		@Override
		public void addOutcomesToSet(Set<String> nodeIds) {
			nodeIds.add(outcomeNodeId);
		}

		@Override
		public void addCandidatesForStartId(Map<String, Boolean> candidatesForStartId, String id) {
			if(outcomeNodeId.equals(id)){
				candidatesForStartId.put(id, false);
			}

		}


	}
}
