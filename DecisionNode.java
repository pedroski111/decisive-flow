package com.fastjet.workflow.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DecisionNode {
	
	/**
	 * Refers to the various synonyms used by this decision node.
	 *
	 * @return the string[]
	 */
	public String[] value() default "";
}
