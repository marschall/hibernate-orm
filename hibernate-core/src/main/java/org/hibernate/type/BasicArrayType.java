/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.type;

import java.lang.reflect.Array;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.metamodel.model.convert.spi.BasicValueConverter;
import org.hibernate.sql.ast.Clause;
import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.JdbcLiteralFormatter;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.descriptor.jdbc.JdbcTypeIndicators;
import org.hibernate.type.descriptor.jdbc.internal.JdbcLiteralFormatterArray;
import org.hibernate.type.spi.TypeConfiguration;

/**
 * A type that maps between {@link java.sql.Types#ARRAY ARRAY} and {@code T[]}
 *
 * @author Jordan Gigov
 * @author Christian Beikov
 */
public class BasicArrayType<T>
		extends AbstractSingleColumnStandardBasicType<T[]>
		implements AdjustableBasicType<T[]>, BasicPluralType<T[], T> {

	private final BasicType<T> baseDescriptor;
	private final String name;
	private final ValueBinder<T[]> jdbcValueBinder;
	private final ValueExtractor<T[]> jdbcValueExtractor;
	private final JdbcLiteralFormatter<T[]> jdbcLiteralFormatter;
//	private final BasicValueConverter<T[], ?> valueConverter;

	public BasicArrayType(BasicType<T> baseDescriptor, JdbcType arrayJdbcType, JavaType<T[]> arrayTypeDescriptor) {
		super( arrayJdbcType, arrayTypeDescriptor );
		this.baseDescriptor = baseDescriptor;
		this.name = baseDescriptor.getName() + "[]";
		final ValueBinder<T[]> jdbcValueBinder = super.getJdbcValueBinder();
		final ValueExtractor<T[]> jdbcValueExtractor = super.getJdbcValueExtractor();
		final JdbcLiteralFormatter jdbcLiteralFormatter = super.getJdbcLiteralFormatter();
		//noinspection unchecked
		final BasicValueConverter<T, Object> valueConverter = (BasicValueConverter<T, Object>) baseDescriptor.getValueConverter();
		if ( valueConverter != null ) {
			this.jdbcValueBinder = new ValueBinder<>() {
				@Override
				public void bind(PreparedStatement st, T[] value, int index, WrapperOptions options)
						throws SQLException {
					jdbcValueBinder.bind( st, getValue( value, valueConverter, options ), index, options );
				}

				@Override
				public void bind(CallableStatement st, T[] value, String name, WrapperOptions options)
						throws SQLException {
					jdbcValueBinder.bind( st, getValue( value, valueConverter, options ), name, options );
				}

				private T[] getValue(
						T[] value,
						BasicValueConverter<T, Object> valueConverter,
						WrapperOptions options) {
					if ( value == null ) {
						return null;
					}
					final JdbcType elementJdbcType = baseDescriptor.getJdbcType();
					final TypeConfiguration typeConfiguration = options.getSessionFactory().getTypeConfiguration();
					final JdbcType underlyingJdbcType = typeConfiguration.getJdbcTypeRegistry()
							.getDescriptor( elementJdbcType.getDefaultSqlTypeCode() );
					final Class<?> preferredJavaTypeClass = underlyingJdbcType.getPreferredJavaTypeClass( options );
					final Class<?> elementJdbcJavaTypeClass;
					if ( preferredJavaTypeClass == null ) {
						elementJdbcJavaTypeClass = underlyingJdbcType.getJdbcRecommendedJavaTypeMapping(
								null,
								null,
								typeConfiguration
						).getJavaTypeClass();
					}
					else {
						elementJdbcJavaTypeClass = preferredJavaTypeClass;
					}

					if ( value.getClass().getComponentType() == elementJdbcJavaTypeClass ) {
						return value;
					}
					final Object[] array = (Object[]) Array.newInstance( elementJdbcJavaTypeClass, value.length );
					for ( int i = 0; i < value.length; i++ ) {
						array[i] = valueConverter.getRelationalJavaType().unwrap(
								valueConverter.toRelationalValue( value[i] ),
								elementJdbcJavaTypeClass,
								options
						);
					}
					//noinspection unchecked
					return (T[]) array;
				}
			};
			this.jdbcValueExtractor = new ValueExtractor<T[]>() {
				@Override
				public T[] extract(ResultSet rs, int paramIndex, WrapperOptions options) throws SQLException {
					return getValue( jdbcValueExtractor.extract( rs, paramIndex, options ), valueConverter );
				}

				@Override
				public T[] extract(CallableStatement statement, int paramIndex, WrapperOptions options)
						throws SQLException {
					return getValue( jdbcValueExtractor.extract( statement, paramIndex, options ), valueConverter );
				}

				@Override
				public T[] extract(CallableStatement statement, String paramName, WrapperOptions options)
						throws SQLException {
					return getValue( jdbcValueExtractor.extract( statement, paramName, options ), valueConverter );
				}

				private T[] getValue(T[] value, BasicValueConverter<T, Object> valueConverter) {
					if ( value == null ) {
						return null;
					}
					if ( value.getClass().getComponentType() == valueConverter.getDomainJavaType().getJavaTypeClass() ) {
						return value;
					}
					//noinspection unchecked
					final T[] array = (T[]) Array.newInstance(
							valueConverter.getDomainJavaType().getJavaTypeClass(),
							value.length
					);
					for ( int i = 0; i < value.length; i++ ) {
						array[i] = valueConverter.toDomainValue( value[i] );
					}
					return array;
				}
			};
			this.jdbcLiteralFormatter = new JdbcLiteralFormatterArray(
					baseDescriptor.getJavaTypeDescriptor(),
					jdbcLiteralFormatter
			);
//			this.valueConverter = new BasicValueConverter<T[], Object>() {
//
//				@Override
//				public T[] toDomainValue(Object relationalForm) {
//					return null;
//				}
//
//				@Override
//				public Object toRelationalValue(T[] domainForm) {
//					return null;
//				}
//
//				@Override
//				public JavaType<T[]> getDomainJavaType() {
//					return getJavaTypeDescriptor();
//				}
//
//				@Override
//				public JavaType<Object> getRelationalJavaType() {
//					return null;
//				}
//			};
		}
		else {
			this.jdbcValueBinder = jdbcValueBinder;
			this.jdbcValueExtractor = jdbcValueExtractor;
			this.jdbcLiteralFormatter = jdbcLiteralFormatter;
//			this.valueConverter = null;
		}
	}

	@Override
	public BasicType<T> getElementType() {
		return baseDescriptor;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	protected boolean registerUnderJavaType() {
		return true;
	}

	@Override
	public ValueExtractor<T[]> getJdbcValueExtractor() {
		return jdbcValueExtractor;
	}

	@Override
	public ValueBinder<T[]> getJdbcValueBinder() {
		return jdbcValueBinder;
	}

	@Override
	public JdbcLiteralFormatter getJdbcLiteralFormatter() {
		return jdbcLiteralFormatter;
	}

//	@Override
//	public BasicValueConverter<T[], ?> getValueConverter() {
//		return valueConverter;
//	}
	
	@Override
	public int forEachJdbcValue(Object value, int offset, JdbcValuesConsumer valuesConsumer, SharedSessionContractImplementor session) {
		// we need to convert to the correct component type so that
		// JdbcJavaType().isInstance( bindValue ) will return true
		// otherwise no JdbcParameterBindingImpl instance can be created
		// 
		// Integer[].class.isInstance( new int[0] ) will return false and vice versa
	
		final Class<?> javaComponentType = getJavaTypeDescriptor().getJavaTypeClass().getComponentType();
		if ( value.getClass().isArray() ) {
			Class<?> valueComponentType = value.getClass().getComponentType();
			if ( valueComponentType.isPrimitive() ) {
				if ( javaComponentType.isPrimitive() ) {
					// primitive on both sides, assume types match
					valuesConsumer.consume( offset, value, getJdbcMapping() );
				}
				else {
					final Object[] wrapperArray = primitiveToWrapperArray( value, javaComponentType, valueComponentType );

					valuesConsumer.consume( offset, wrapperArray, getJdbcMapping() );
				}
			}
			else {
				if ( javaComponentType.isPrimitive() ) {
					// convert wrapper to primitive
					final Object primitiveArray = wrapperToPrimitiveArray( value, javaComponentType );

					valuesConsumer.consume( offset, primitiveArray, getJdbcMapping() );
				}
				else {
					// both are reference types
					final Class<?> arrayClass = Array.newInstance(
							javaComponentType,
							0
							).getClass();
					valuesConsumer.consume( offset, getJavaTypeDescriptor().unwrap( (T[]) value, arrayClass, session ), getJdbcMapping() );
				}
			}
		}
		else if ( value instanceof Collection ) {
			final Collection<?> collection = (Collection<?>) value;
			final Object array;
			if ( javaComponentType.isPrimitive() ) {
				array = toPrimitiveArray( javaComponentType, collection );
			}
			else {
				array = toReferenceArray( javaComponentType, collection );
			}
			
			valuesConsumer.consume( offset, array, getJdbcMapping() );
		}
		else {
			throw new IllegalStateException("unknown type");
		}
		return getJdbcTypeCount();
	}
	
	private static Object toPrimitiveArray(Class<?> javaComponentType, Collection<?> collection) {
		final int length = collection.size();
		final Object array = Array.newInstance(
				javaComponentType,
				length );
		if ( javaComponentType == boolean.class ) {
			int i = 0;
			for ( Object each : collection ) {
				( (boolean[]) array )[i++] = (Boolean) each;
			}
		}
		else if ( javaComponentType == char.class ) {
			int i = 0;
			for ( Object each : collection ) {
				( (char[]) array )[i++] = (Character) each;
			}
		}
		else if ( javaComponentType == byte.class ) {
			int i = 0;
			for ( Object each : collection ) {
				( (byte[]) array )[i++] = (Byte) each;
			}
		}
		else if ( javaComponentType == short.class ) {
			int i = 0;
			for ( Object each : collection ) {
				( (short[]) array )[i++] = (Short) each;
			}
		}
		else if ( javaComponentType == int.class ) {
			int i = 0;
			for ( Object each : collection ) {
				( (short[]) array )[i++] = (Short) each;
			}
		}
		else if ( javaComponentType == long.class ) {
			int i = 0;
			for ( Object each : collection ) {
				( (long[]) array )[i++] = (Long) each;
			}
		}
		else if ( javaComponentType == float.class ) {
			int i = 0;
			for ( Object each : collection ) {
				( (float[]) array )[i++] = (Float) each;
			}
		}
		else if ( javaComponentType == double.class ) {
			int i = 0;
			for ( Object each : collection ) {
				( (double[]) array )[i++] = (Double) each;
			}
		}
		else {
			throw new IllegalArgumentException( "Unrecognized primitive type class : " + javaComponentType.getName() );
		}
		return array;
	}

	private static Object[] toReferenceArray(Class<?> javaComponentType, Collection<?> collection) {
		final Object[] array = (Object[]) Array.newInstance(
				javaComponentType,
				collection.size()
				);
		return collection.toArray(array);
	}

	private static Object wrapperToPrimitiveArray(Object value, final Class<?> javaComponentType) {
		final int length = Array.getLength( value );
		final Object array = Array.newInstance(
				javaComponentType,
				length );

		if ( javaComponentType == boolean.class ) {
			for ( int i = 0; i < length; i++ ) {
				( (boolean[]) array )[i] = (Boolean) ( (Object[]) value )[i];
			}
		}
		else if ( javaComponentType == char.class ) {
			for ( int i = 0; i < length; i++ ) {
				( (char[]) array )[i] = (Character) ( (Object[]) value )[i];
			}
		}
		else if ( javaComponentType == byte.class ) {
			for ( int i = 0; i < length; i++ ) {
				( (byte[]) array )[i] = (Byte) ( (Object[]) value )[i];
			}
		}
		else if ( javaComponentType == short.class ) {
			for ( int i = 0; i < length; i++ ) {
				( (short[]) array )[i] = (Short) ( (Object[]) value )[i];
			}
		}
		else if ( javaComponentType == int.class ) {
			for ( int i = 0; i < length; i++ ) {
				( (int[]) array )[i] = (Integer) ( (Object[]) value )[i];
			}
		}
		else if ( javaComponentType == long.class ) {
			for ( int i = 0; i < length; i++ ) {
				( (long[]) array )[i] = (Long) ( (Object[]) value )[i];
			}
		}
		else if ( javaComponentType == float.class ) {
			for ( int i = 0; i < length; i++ ) {
				( (float[]) array )[i] = (Float) ( (Object[]) value )[i];
			}
		}
		else if ( javaComponentType == double.class ) {
			for ( int i = 0; i < length; i++ ) {
				( (double[]) array )[i] = (Double) ( (Object[]) value )[i];
			}
		}
		else {
			throw new IllegalArgumentException( "Unrecognized primitive type class : " + javaComponentType.getName() );
		}
		return array;
	}

	private static Object[] primitiveToWrapperArray(Object value, Class<?> javaComponentType, Class<?> valueComponentType) {
		final int length = Array.getLength( value );
		final Object[] array = (Object[]) Array.newInstance(
				javaComponentType,
				length );
		if ( valueComponentType == boolean.class ) {
			for ( int i = 0; i < length; i++ ) {
				array[i] = ( (boolean[]) value )[i];
			}
		}
		else if ( valueComponentType == char.class ) {
			for ( int i = 0; i < length; i++ ) {
				array[i] = ( (char[]) value )[i];
			}
		}
		else if ( valueComponentType == byte.class ) {
			for ( int i = 0; i < length; i++ ) {
				array[i] = ( (byte[]) value )[i];
			}
		}
		else if ( valueComponentType == short.class ) {
			for ( int i = 0; i < length; i++ ) {
				array[i] = ( (short[]) value )[i];
			}
		}
		else if ( valueComponentType == int.class ) {
			for ( int i = 0; i < length; i++ ) {
				array[i] = ( (int[]) value )[i];
			}
		}
		else if ( valueComponentType == long.class ) {
			for ( int i = 0; i < length; i++ ) {
				array[i] = ( (long[]) value )[i];
			}
		}
		else if ( valueComponentType == float.class ) {
			for ( int i = 0; i < length; i++ ) {
				array[i] = ( (float[]) value )[i];
			}
		}
		else if ( valueComponentType == double.class ) {
			for ( int i = 0; i < length; i++ ) {
				array[i] = ( (double[]) value )[i];
			}
		}
		else {
			throw new IllegalArgumentException( "Unrecognized primitive type class : " + valueComponentType.getName() );
		}
		return array;
	}

	@Override
	public <X> BasicType<X> resolveIndicatedType(JdbcTypeIndicators indicators, JavaType<X> domainJtd) {
		// TODO: maybe fallback to some encoding by default if the DB doesn't support arrays natively?
		//  also, maybe move that logic into the ArrayJdbcType
		//noinspection unchecked
		return (BasicType<X>) this;
	}
}
