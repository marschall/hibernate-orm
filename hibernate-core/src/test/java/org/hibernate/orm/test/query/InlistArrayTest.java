/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.query;

import static org.junit.Assert.assertTrue;

import java.util.Arrays;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;


/**
 * @author Philippe Marschall
 */
@RequiresDialectFeature(feature = DialectFeatureChecks.SupportsStandardArrays.class)
@Jpa(
		annotatedClasses = { InlistArrayTest.Person.class },
		settingProviders = {
				@SettingProvider(
						settingName = AvailableSettings.STATEMENT_INSPECTOR,
						provider = DefaultSQLStatementInspectorSettingProvider.class
				)
		}
)
public class InlistArrayTest {

	@BeforeEach
	protected void afterEntityManagerFactoryBuilt(EntityManagerFactoryScope scope) {
		scope.inTransaction( entityManager -> {
			for ( int i = 1; i < 10; i++ ) {
				Person person = new Person();
				person.setId( i );
				person.setName( String.format( "Person nr %d", i ) );

				entityManager.persist( person );
			}
		} );
	}

	@Test
	@SkipForDialect(dialectClass = HSQLDialect.class)
	public void testInlistArray(EntityManagerFactoryScope scope) {
		validateInClauseParameterPadding( scope, "=ANY(?)", 1 );
		validateInClauseParameterPadding( scope, "=ANY(?)", 1, 2 );
		validateInClauseParameterPadding( scope, "=ANY(?)", 1, 2, 3 );
		validateInClauseParameterPadding( scope, "=ANY(?)", 1, 2, 3, 4 );
		validateInClauseParameterPadding( scope, "=ANY(?)", 1, 2, 3, 4, 5 );
		validateInClauseParameterPadding( scope, "=ANY(?)", 1, 2, 3, 4, 5, 6 );
		validateInClauseParameterPadding( scope, "=ANY(?)", 1, 2, 3, 4, 5, 6, 7 );
		validateInClauseParameterPadding( scope, "=ANY(?)", 1, 2, 3, 4, 5, 6, 7, 8 );
		validateInClauseParameterPadding( scope, "=ANY(?)", 1, 2, 3, 4, 5, 6, 7, 8, 9 );
		validateInClauseParameterPadding( scope, "=ANY(?)", 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 );
	}
	
	@Test
	@RequiresDialect(HSQLDialect.class)
	public void testInlistArrayHsql(EntityManagerFactoryScope scope) {
		validateInClauseParameterPadding( scope, " IN(UNNEST(?))", 1 );
		validateInClauseParameterPadding( scope, " IN(UNNEST(?))", 1, 2 );
		validateInClauseParameterPadding( scope, " IN(UNNEST(?))", 1, 2, 3 );
		validateInClauseParameterPadding( scope, " IN(UNNEST(?))", 1, 2, 3, 4 );
		validateInClauseParameterPadding( scope, " IN(UNNEST(?))", 1, 2, 3, 4, 5 );
		validateInClauseParameterPadding( scope, " IN(UNNEST(?))", 1, 2, 3, 4, 5, 6 );
		validateInClauseParameterPadding( scope, " IN(UNNEST(?))", 1, 2, 3, 4, 5, 6, 7 );
		validateInClauseParameterPadding( scope, " IN(UNNEST(?))", 1, 2, 3, 4, 5, 6, 7, 8 );
		validateInClauseParameterPadding( scope, " IN(UNNEST(?))", 1, 2, 3, 4, 5, 6, 7, 8, 9 );
		validateInClauseParameterPadding( scope, " IN(UNNEST(?))", 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 );
	}

	private void validateInClauseParameterPadding(
			EntityManagerFactoryScope scope,
			String expectedInClause,
			Integer... ids) {
		final SQLStatementInspector sqlStatementInterceptor = scope.getStatementInspector( SQLStatementInspector.class );
		sqlStatementInterceptor.clear();

		scope.inTransaction( entityManager -> {
			entityManager.createQuery(
							"select p " +
									"from Person p " +
									"where p.id in :ids" )
					.setParameter( "ids", Arrays.asList( ids ) )
					.getResultList();
		} );

		assertTrue( sqlStatementInterceptor.getSqlQueries().get( 0 ).endsWith( expectedInClause ) );
	}

	@Entity(name = "Person")
	public static class Person {

		@Id
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
