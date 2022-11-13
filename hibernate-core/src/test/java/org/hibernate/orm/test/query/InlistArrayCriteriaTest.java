/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.HSQLDialect;
import org.hibernate.testing.jdbc.SQLStatementInspector;
import org.hibernate.testing.orm.jdbc.DefaultSQLStatementInspectorSettingProvider;
import org.hibernate.testing.orm.junit.DialectFeatureChecks;
import org.hibernate.testing.orm.junit.EntityManagerFactoryScope;
import org.hibernate.testing.orm.junit.Jpa;
import org.hibernate.testing.orm.junit.RequiresDialect;
import org.hibernate.testing.orm.junit.RequiresDialectFeature;
import org.hibernate.testing.orm.junit.SettingProvider;
import org.hibernate.testing.orm.junit.SkipForDialect;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.ParameterExpression;
import jakarta.persistence.criteria.Root;

/**
 * @author Philippe Marschall
 */
@RequiresDialectFeature(feature = DialectFeatureChecks.SupportsStandardArrays.class)
@Jpa(
		annotatedClasses = InlistArrayCriteriaTest.Document.class,
		settingProviders = {
				@SettingProvider(
						settingName = AvailableSettings.STATEMENT_INSPECTOR,
						provider = DefaultSQLStatementInspectorSettingProvider.class
				)
		}
)
public class InlistArrayCriteriaTest {

	@BeforeAll
	protected void afterEntityManagerFactoryBuilt(EntityManagerFactoryScope scope) {
		scope.inTransaction( entityManager -> {
			Document document = new Document();
			document.setName( "A" );
			entityManager.persist( document );
		} );
	}

	@Test
	@SkipForDialect(dialectClass = HSQLDialect.class)
	public void testInlistArray(EntityManagerFactoryScope scope) {
		final SQLStatementInspector statementInspector = scope.getStatementInspector( SQLStatementInspector.class );
		statementInspector.clear();

		scope.inTransaction( entityManager -> {
			CriteriaBuilder cb = entityManager.getCriteriaBuilder();
			CriteriaQuery<Integer> query = cb.createQuery( Integer.class );
			Root<Document> document = query.from( Document.class );

			ParameterExpression<List> inClauseParams = cb.parameter( List.class, "ids" );

			query.select( document.get( "id" ) )
					.where( document.get( "id" ).in( inClauseParams ) );

			List<Integer> ids = entityManager.createQuery( query )
					.setParameter( "ids", Arrays.asList( 1, 2, 3, 4, 5 ) )
					.getResultList();
			assertEquals( 1, ids.size() );
		} );

		assertTrue( statementInspector.getSqlQueries().get( 0 ).endsWith( " = ANY(?)" ) );
	}
	
	@Test
	@RequiresDialect(HSQLDialect.class)
	public void testInlistArrayHsql(EntityManagerFactoryScope scope) {
		final SQLStatementInspector statementInspector = scope.getStatementInspector( SQLStatementInspector.class );
		statementInspector.clear();
		
		scope.inTransaction( entityManager -> {
			CriteriaBuilder cb = entityManager.getCriteriaBuilder();
			CriteriaQuery<Integer> query = cb.createQuery( Integer.class );
			Root<Document> document = query.from( Document.class );
			
			ParameterExpression<List> inClauseParams = cb.parameter( List.class, "ids" );
			
			query.select( document.get( "id" ) )
			.where( document.get( "id" ).in( inClauseParams ) );
			
			List<Integer> ids = entityManager.createQuery( query )
					.setParameter( "ids", Arrays.asList( 1, 2, 3, 4, 5 ) )
					.getResultList();
			assertEquals( 1, ids.size() );
		} );
		
		assertTrue( statementInspector.getSqlQueries().get( 0 ).endsWith( " IN(UNNEST(?))" ) );
	}

	@Test
	public void testInlistArrayForExpressions(EntityManagerFactoryScope scope) {
		final SQLStatementInspector statementInspector = scope.getStatementInspector( SQLStatementInspector.class );
		statementInspector.clear();

		scope.inTransaction( entityManager -> {
			CriteriaBuilder cb = entityManager.getCriteriaBuilder();
			CriteriaQuery<Integer> query = cb.createQuery( Integer.class );
			Root<Document> document = query.from( Document.class );

			query.select( document.get( "id" ) )
					.where(
							document.get( "id" ).in(
							document.get( "id" ),
							document.get( "id" ),
							document.get( "id" )
					)
			);

			List<Integer> ids = entityManager.createQuery( query )
					.getResultList();
			assertEquals( 1, ids.size() );
		} );

		assertTrue( statementInspector.getSqlQueries().get( 0 ).endsWith( "in(d1_0.id,d1_0.id,d1_0.id)" ) );
	}

	@Entity(name = "Document")
	public static class Document {

		@Id
		@GeneratedValue
		private Integer id;

		private String name;

		public Integer getId() {
			return id;
		}

		public void setId(Integer id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}


}
