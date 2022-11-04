/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.sql.ast.tree.predicate;

import org.hibernate.metamodel.mapping.JdbcMappingContainer;
import org.hibernate.sql.ast.SqlAstWalker;
import org.hibernate.sql.ast.tree.expression.Expression;

/**
 * @author Philippe Marschall
 */
public class InListArrayPredicate extends AbstractPredicate {
	private final Expression testExpression;
	private final Expression arrayExpression;

	public InListArrayPredicate(Expression testExpression) {
		this( testExpression, null );
	}

	public InListArrayPredicate(Expression testExpression, boolean negated, JdbcMappingContainer expressionType) {
		this( testExpression, null, negated, expressionType );
	}

	public InListArrayPredicate(
			Expression testExpression,
			Expression arrayExpression) {
		this( testExpression, arrayExpression, false, null );
	}

	public InListArrayPredicate(
			Expression testExpression,
			Expression arrayExpression,
			boolean negated,
			JdbcMappingContainer expressionType) {
		super( expressionType, negated );
		this.testExpression = testExpression;
		this.arrayExpression = arrayExpression;
	}

	public Expression getTestExpression() {
		return testExpression;
	}
	
	public Expression getArrayExpression() {
		return arrayExpression;
	}

	@Override
	public void accept(SqlAstWalker sqlTreeWalker) {
		sqlTreeWalker.visitInListArrayPredicate( this );
	}
}
