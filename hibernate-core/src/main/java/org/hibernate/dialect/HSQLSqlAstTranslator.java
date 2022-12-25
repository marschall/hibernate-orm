/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.dialect;

import java.util.List;
import java.util.function.Consumer;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.mapping.JdbcMappingContainer;
import org.hibernate.query.sqm.BinaryArithmeticOperator;
import org.hibernate.query.sqm.ComparisonOperator;
import org.hibernate.sql.ast.SqlAstNodeRenderingMode;
import org.hibernate.sql.ast.spi.AbstractSqlAstTranslator;
import org.hibernate.sql.ast.spi.SqlSelection;
import org.hibernate.sql.ast.tree.Statement;
import org.hibernate.sql.ast.tree.expression.BinaryArithmeticExpression;
import org.hibernate.sql.ast.tree.expression.CaseSearchedExpression;
import org.hibernate.sql.ast.tree.expression.CaseSimpleExpression;
import org.hibernate.sql.ast.tree.expression.Expression;
import org.hibernate.sql.ast.tree.expression.Literal;
import org.hibernate.sql.ast.tree.expression.SqlTuple;
import org.hibernate.sql.ast.tree.expression.Summarization;
import org.hibernate.sql.ast.tree.predicate.BooleanExpressionPredicate;
import org.hibernate.sql.ast.tree.select.QueryPart;
import org.hibernate.sql.exec.spi.JdbcOperation;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.descriptor.jdbc.ArrayJdbcType;

/**
 * A SQL AST translator for HSQL.
 *
 * @author Christian Beikov
 */
public class HSQLSqlAstTranslator<T extends JdbcOperation> extends AbstractSqlAstTranslator<T> {

	public HSQLSqlAstTranslator(SessionFactoryImplementor sessionFactory, Statement statement) {
		super( sessionFactory, statement );
	}

	@Override
	protected void renderExpressionAsClauseItem(Expression expression) {
		expression.accept( this );
	}

	@Override
	public void visitBooleanExpressionPredicate(BooleanExpressionPredicate booleanExpressionPredicate) {
		final boolean isNegated = booleanExpressionPredicate.isNegated();
		if ( isNegated ) {
			appendSql( "not(" );
		}
		booleanExpressionPredicate.getExpression().accept( this );
		if ( isNegated ) {
			appendSql( CLOSE_PARENTHESIS );
		}
	}

	@Override
	protected boolean supportsArrayConstructor() {
		return true;
	}

	@Override
	protected boolean supportsWithClauseInSubquery() {
		// Doesn't support correlations in the WITH clause
		return false;
	}

	@Override
	protected boolean supportsRecursiveClauseArrayAndRowEmulation() {
		// Even though HSQL supports the array constructor, it's illegal to use arrays in CTEs
		return false;
	}

	@Override
	protected void visitRecursivePath(Expression recursivePath, int sizeEstimate) {
		// HSQL determines the type and size of a column in a recursive CTE based on the expression of the non-recursive part
		// Due to that, we have to cast the path in the non-recursive path to a varchar of appropriate size to avoid data truncation errors
		if ( sizeEstimate == -1 ) {
			super.visitRecursivePath( recursivePath, sizeEstimate );
		}
		else {
			appendSql( "cast(" );
			recursivePath.accept( this );
			appendSql( " as varchar(" );
			appendSql( sizeEstimate );
			appendSql( "))" );
		}
	}

	// HSQL does not allow CASE expressions where all result arms contain plain parameters.
	// At least one result arm must provide some type context for inference,
	// so we cast the first result arm if we encounter this condition

	@Override
	protected void visitAnsiCaseSearchedExpression(
			CaseSearchedExpression expression,
			Consumer<Expression> resultRenderer) {
		if ( getParameterRenderingMode() == SqlAstNodeRenderingMode.DEFAULT && areAllResultsParameters( expression )
				|| areAllResultsPlainParametersOrLiterals( expression ) ) {
			final List<CaseSearchedExpression.WhenFragment> whenFragments = expression.getWhenFragments();
			final Expression firstResult = whenFragments.get( 0 ).getResult();
			super.visitAnsiCaseSearchedExpression(
					expression,
					e -> {
						if ( e == firstResult ) {
							renderCasted( e );
						}
						else {
							resultRenderer.accept( e );
						}
					}
			);
		}
		else {
			super.visitAnsiCaseSearchedExpression( expression, resultRenderer );
		}
	}

	@Override
	protected void visitAnsiCaseSimpleExpression(
			CaseSimpleExpression expression,
			Consumer<Expression> resultRenderer) {
		if ( getParameterRenderingMode() == SqlAstNodeRenderingMode.DEFAULT && areAllResultsParameters( expression )
				|| areAllResultsPlainParametersOrLiterals( expression ) ) {
			final List<CaseSimpleExpression.WhenFragment> whenFragments = expression.getWhenFragments();
			final Expression firstResult = whenFragments.get( 0 ).getResult();
			super.visitAnsiCaseSimpleExpression(
					expression,
					e -> {
						if ( e == firstResult ) {
							renderCasted( e );
						}
						else {
							resultRenderer.accept( e );
						}
					}
			);
		}
		else {
			super.visitAnsiCaseSimpleExpression( expression, resultRenderer );
		}
	}

	protected boolean areAllResultsPlainParametersOrLiterals(CaseSearchedExpression caseSearchedExpression) {
		final List<CaseSearchedExpression.WhenFragment> whenFragments = caseSearchedExpression.getWhenFragments();
		final Expression firstResult = whenFragments.get( 0 ).getResult();
		if ( isParameter( firstResult ) && getParameterRenderingMode() == SqlAstNodeRenderingMode.DEFAULT
				|| isLiteral( firstResult ) ) {
			for ( int i = 1; i < whenFragments.size(); i++ ) {
				final Expression result = whenFragments.get( i ).getResult();
				if ( isParameter( result ) ) {
					if ( getParameterRenderingMode() != SqlAstNodeRenderingMode.DEFAULT ) {
						return false;
					}
				}
				else if ( !isLiteral( result ) ) {
					return false;
				}
			}
			return true;
		}
		return false;
	}

	protected boolean areAllResultsPlainParametersOrLiterals(CaseSimpleExpression caseSimpleExpression) {
		final List<CaseSimpleExpression.WhenFragment> whenFragments = caseSimpleExpression.getWhenFragments();
		final Expression firstResult = whenFragments.get( 0 ).getResult();
		if ( isParameter( firstResult ) && getParameterRenderingMode() == SqlAstNodeRenderingMode.DEFAULT
				|| isLiteral( firstResult ) ) {
			for ( int i = 1; i < whenFragments.size(); i++ ) {
				final Expression result = whenFragments.get( i ).getResult();
				if ( isParameter( result ) ) {
					if ( getParameterRenderingMode() != SqlAstNodeRenderingMode.DEFAULT ) {
						return false;
					}
				}
				else if ( !isLiteral( result ) ) {
					return false;
				}
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean supportsFilterClause() {
		return true;
	}

	@Override
	public void visitOffsetFetchClause(QueryPart queryPart) {
		if ( supportsOffsetFetchClause() ) {
			assertRowsOnlyFetchClauseType( queryPart );
			renderOffsetFetchClause( queryPart, true );
		}
		else {
			renderLimitOffsetClause( queryPart );
		}
	}

	@Override
	protected void renderSelectExpression(Expression expression) {
		renderSelectExpressionWithCastedOrInlinedPlainParameters( expression );
	}

	@Override
	protected void renderSelectTupleComparison(
			List<SqlSelection> lhsExpressions,
			SqlTuple tuple,
			ComparisonOperator operator) {
		emulateSelectTupleComparison( lhsExpressions, tuple.getExpressions(), operator, true );
	}

	protected void renderComparison(Expression lhs, ComparisonOperator operator, Expression rhs) {
		final JdbcMappingContainer lhsExpressionType = lhs.getExpressionType();
		if ( lhsExpressionType == null ) {
			renderComparisonStandard( lhs, operator, rhs );
			return;
		}
		switch ( operator ) {
			case EQUAL:
				if ( lhsExpressionType.getJdbcMappings().get( 0 ).getJdbcType().getJdbcTypeCode() == SqlTypes.ARRAY ) {
					lhs.accept( this );
					appendSql( " IN(" );
					rhs.accept( this );
					appendSql( ')' );
				}
				else {
					renderComparisonStandard( lhs, operator, rhs );
				}
				break;
			case NOT_EQUAL:
				if ( lhsExpressionType.getJdbcMappings().get( 0 ).getJdbcType().getJdbcTypeCode() == SqlTypes.ARRAY ) {
					lhs.accept( this );
					appendSql( " NOT IN(" );
					rhs.accept( this );
					appendSql( ')' );
				}
				else {
					renderComparisonStandard( lhs, operator, rhs );
				}
				break;
			case DISTINCT_FROM:
			case NOT_DISTINCT_FROM:
				if ( lhsExpressionType.getJdbcMappings().get( 0 ).getJdbcType() instanceof ArrayJdbcType ) {
					// HSQL implements distinct from semantics for arrays
					lhs.accept( this );
					appendSql( operator == ComparisonOperator.DISTINCT_FROM ? "<>" : "=" );
					rhs.accept( this );
				}
				else {
					// HSQL does not like parameters in the distinct from predicate
					render( lhs, SqlAstNodeRenderingMode.NO_PLAIN_PARAMETER );
					appendSql( operator.sqlText() );
					render( rhs, SqlAstNodeRenderingMode.NO_PLAIN_PARAMETER );
				}
				break;
			default:
				renderComparisonStandard( lhs, operator, rhs );
				break;
		}
	}

	@Override
	protected void renderPartitionItem(Expression expression) {
		if ( expression instanceof Literal ) {
			appendSql( "'0' || '0'" );
		}
		else if ( expression instanceof Summarization ) {
			// This could theoretically be emulated by rendering all grouping variations of the query and
			// connect them via union all but that's probably pretty inefficient and would have to happen
			// on the query spec level
			throw new UnsupportedOperationException( "Summarization is not supported by DBMS" );
		}
		else {
			expression.accept( this );
		}
	}

	@Override
	protected boolean supportsRowValueConstructorSyntax() {
		return false;
	}

	@Override
	protected boolean supportsRowValueConstructorSyntaxInInList() {
		return false;
	}

	@Override
	protected boolean supportsRowValueConstructorSyntaxInQuantifiedPredicates() {
		return false;
	}

	@Override
	protected String getFromDual() {
		return " from (values(0))";
	}

	@Override
	protected String getFromDualForSelectOnly() {
		return getFromDual();
	}

	private boolean supportsOffsetFetchClause() {
		return true;
	}

	@Override
	public void visitBinaryArithmeticExpression(BinaryArithmeticExpression arithmeticExpression) {
		final BinaryArithmeticOperator operator = arithmeticExpression.getOperator();
		if ( operator == BinaryArithmeticOperator.MODULO ) {
			append( "mod" );
			appendSql( OPEN_PARENTHESIS );
			arithmeticExpression.getLeftHandOperand().accept( this );
			appendSql( ',' );
			arithmeticExpression.getRightHandOperand().accept( this );
			appendSql( CLOSE_PARENTHESIS );
		}
		else {
			appendSql( OPEN_PARENTHESIS );
			render( arithmeticExpression.getLeftHandOperand(), SqlAstNodeRenderingMode.NO_PLAIN_PARAMETER );
			appendSql( arithmeticExpression.getOperator().getOperatorSqlTextString() );
			render( arithmeticExpression.getRightHandOperand(), SqlAstNodeRenderingMode.NO_PLAIN_PARAMETER );
			appendSql( CLOSE_PARENTHESIS );
		}
	}
}
