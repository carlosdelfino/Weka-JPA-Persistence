package org.weka.jpa;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Query;
import javax.persistence.Temporal;

import org.slf4j.Logger; 

import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;
import weka.core.converters.ArffSaver;

public class Weka2JPAHelper {

	private Logger log;

	private EntityManager em;

	private Set<Class<?>> ignoreFieldsTypeOf = new HashSet<>();

	/**
	 * Caso não se esteja usando CDI (como WELD) é preciso fornecer manualmente
	 * o Logger e EntityManager para a classe;
	 * 
	 * Em containers gerenciados por CDI basta solicitar a instancia da classe e
	 * esta será injetada com o Logger correto e o EntityManager adequado.
	 * 
	 * @param p_logger
	 * @param p_em
	 */
	@Inject
	public Weka2JPAHelper(Logger p_logger, @Named("SocialSLA") EntityManager p_em) {
		log = p_logger;
		em = p_em;
	}

	/**
	 * Cria o arquivo ARFF com base na classe da entidade informada, e a lista
	 * de entidades instancianadas.
	 * 
	 * Deve ser informado o nome do arquivo a ser criado, a classe da entidade
	 * que é a base e referencia para construir o arquivo ARFF, esta classe será
	 * usada para obter informações base dos parametros do Arquivo ARFF.
	 * 
	 * Se for informado a Collection esta deverá ser uma coleção de instancias
	 * da classe informada como base, caso não informado a classe base será
	 * usasda para efetuar um "SELECT" pelo JPA na camada de persistência.
	 * 
	 * @param p_file
	 * @param p_entityClass
	 * @param p_list
	 * @throws IOException
	 */
	public <E> void save(File p_file, Class<E> p_entityClass, Collection<E> p_list) throws IOException {
		Instances l_data = createAttributesAndInstances(p_entityClass, p_list);

		ArffSaver saver = new ArffSaver();
		saver.setInstances(l_data);
		saver.setFile(p_file);

		saver.writeBatch();
	}

	/**
	 * Cria o arquivo ARFF com base na classe da entidade informada, consultando
	 * a camada de persitencia injetada pela lista de objetos.
	 * 
	 * Deve ser informado o nome do arquivo a ser criado, a classe da entidade
	 * que é a base e referencia para construir o arquivo ARFF, esta classe será
	 * usada para obter informações base dos parametros do Arquivo ARFF.
	 * 
	 * Se for informado a Collection esta deverá ser uma coleção de instancias
	 * da classe informada como base, caso não informado a classe base será
	 * usasda para efetuar um "SELECT" pelo JPA na camada de persistência.
	 * 
	 * @param p_file
	 * @param p_entityClass
	 * @throws IOException
	 */
	public <E> void save(File p_file, Class<E> p_entityClass) throws IOException {

		Instances l_data = createAttributesAndInstances(p_entityClass, null);

		ArffSaver saver = new ArffSaver();
		saver.setInstances(l_data);
		saver.setFile(p_file);

		saver.writeBatch();
	}

	/**
	 * Cria as instancias usadas para construir a seção dados, mas antes
	 * constroi o cabeçalho com as informações de atributos do arquivo ARFF
	 * 
	 * @param p_entityClass
	 * @param p_list
	 * @return
	 */
	private <E> Instances createAttributesAndInstances(Class<E> p_entityClass, Collection<E> p_list) {

		if (!p_entityClass.isAnnotationPresent(Entity.class)) {
			throw new NotEntityWEKAJPARuntimeException();
		}

		Field[] l_fields = p_entityClass.getDeclaredFields();

		Map<Attribute, Field> l_mapAttributeToField = new HashMap<>(l_fields.length);
		Map<Attribute, ArrayList<String>> l_mapAttributeToRefVAlues = new HashMap<>(l_fields.length);

		ArrayList<Attribute> l_atts = createAttributes(l_mapAttributeToRefVAlues, l_mapAttributeToField, l_fields);

		Instances l_data = populateInstanceWithData(l_mapAttributeToRefVAlues, l_mapAttributeToField, l_atts,
				p_entityClass, p_list);

		return l_data;
	}

	/**
	 * Cria os atributos usados no cabeçalho e seus metadados para aferição dos
	 * tipos.
	 * 
	 * @param l_fields
	 * @param l_mapAttributeToRefVAlues
	 * @param l_mapAttributeToField
	 * @return
	 */
	private <E> ArrayList<Attribute> createAttributes(Map<Attribute, ArrayList<String>> l_mapAttributeToRefVAlues,
			Map<Attribute, Field> l_mapAttributeToField, Field[] l_fields) {

		ArrayList<Attribute> l_atts = new ArrayList<Attribute>(l_fields.length);

		for (Field l_field : l_fields) {
			Attribute l_att = null;
			Annotation l_annot;
			Class<?> l_type = l_field.getType();
			if (ignoreFieldsTypeOf.contains(l_type) || l_field.getName().equals("serialVersionUID")) {
				continue;
			}

			if ((l_annot = l_field.getDeclaredAnnotation(ManyToMany.class)) != null) {
				l_att = createAttributeFromManyToMany(l_field);
			} else if ((l_annot = l_field.getDeclaredAnnotation(ManyToOne.class)) != null) {

				ArrayList<String> l_refValues = new ArrayList<>();

				String l_qlString = "SELECT E FROM " + l_type.getSimpleName() + " E ";
				Query l_query = em.createQuery(l_qlString);
				List<E> l_list = l_query.getResultList();

				for (Object l_object : l_list) {
					// TODO: how to identify the best way to convert the child
					// entity in a string or number to be referenced, in
					// addition you need to worry about the correct order of
					// obtaining or using an index synchronized with the
					// database.
					// TODO: An alternative would be the annotation specifies
					// Weka to indicate a method for obtaining a string or
					// double / float representing the object. Remember that
					// this field should also be indexed and stored in the
					// database, Commission is therefore proposing should be
					// noted as a Column type, Temporal, Id, etc.
					l_refValues.add(l_object.toString());
				}
				l_att = createAttributeFromManyToOne(l_field, l_refValues);
				l_mapAttributeToRefVAlues.put(l_att, l_refValues);
			} else if ((l_annot = l_field.getDeclaredAnnotation(OneToMany.class)) != null) {
				l_att = createAttributeFromOneToMany(l_field);
			} else if ((l_annot = l_field.getDeclaredAnnotation(OneToOne.class)) != null) {
				l_att = createAttributeFromOneToOne(l_field);
			} else if ((l_annot = l_field.getDeclaredAnnotation(Temporal.class)) != null) {
				l_att = createAttributeFromTemporal(l_field);
			} else if ((l_annot = l_field.getDeclaredAnnotation(Column.class)) != null) {
				l_att = createAttributeFromColumn(l_field);
			} else {
				// qualquer outra anotação será ignorada
				continue;
			}
			l_atts.add(l_att);
			l_mapAttributeToField.put(l_att, l_field);
		}
		return l_atts;
	}

	private <E> Instances populateInstanceWithData(Map<Attribute, ArrayList<String>> p_mapAttributeToRefValues,
			Map<Attribute, Field> p_mapAttributeToField, ArrayList<Attribute> p_atts, Class<E> p_entityClass,
			Collection<E> p_list) {

		Instances l_data = new Instances(p_entityClass.getSimpleName(), p_atts, 0);

		List<E> l_list;
		if (p_list == null) {
			String l_qlString = "SELECT E FROM " + p_entityClass.getSimpleName() + " E ";

			Query l_query = em.createQuery(l_qlString);

			l_list = l_query.getResultList();
		} else
			l_list = new ArrayList<>(p_list);

		for (final E l_object : l_list) {
			final double[] l_vals = new double[l_data.numAttributes()];

			p_mapAttributeToField.forEach((p_att, p_field) -> {
				// To facilitate debugging,
				// Furthermore the Eclipse was not seeing the parameter p_att;
				Attribute l_att = p_att;// to facilitate debugging
				Field l_field = p_field;// to facilitate debugging
				@SuppressWarnings("unchecked")
				E l_obj = (E) l_object;// to facilitate debugging
				ArrayList<Attribute> l_atts = p_atts;// to facilitate debugging

				try {

					int l_attIndex = l_atts.indexOf(l_att);
					Class<?> l_fieldType = l_field.getType();
					l_field.setAccessible(true);

					double l_val = -1;
					if (l_fieldType == String.class) {
						String l_string = (String) l_field.get(l_obj);
						if (l_string != null)
							l_val = l_data.attribute(l_attIndex).addStringValue(l_string);

					} else if (l_fieldType.isAssignableFrom(Number.class)) {
						Double l_double = l_field.getDouble(l_obj);
						if (l_double != null)
							;
						l_val = l_double;
					} else {
						ArrayList<String> l_refValues = p_mapAttributeToRefValues.get(l_att);
						Object l_value = l_field.get(l_obj);
						if (l_value != null) {
							String l_string = l_value.toString();
							l_val = l_refValues.indexOf(l_string);
						}
					}
					l_vals[l_attIndex] = l_val;
				} catch (Exception e) {
					// does nothing ignores the value?
					log.info(e.getMessage());
				}

			});
			DenseInstance l_instance = new DenseInstance(1.0, l_vals);
			for (int l_idx = 0; l_idx < l_vals.length; l_idx++) {
				if (l_vals[l_idx] < 0)
					l_instance.setMissing(l_idx);
			}
			l_data.add(l_instance);

		}

		return l_data;
	}

	private Attribute createAttributeFromTemporal(Field p_field) {
		// TODO Auto-generated method stub
		return null;
	}

	private Attribute createAttributeFromOneToOne(Field p_field) {
		// TODO Auto-generated method stub
		return null;
	}

	private Attribute createAttributeFromOneToMany(Field p_field) {
		// TODO Auto-generated method stub
		return null;
	}

	private Attribute createAttributeFromManyToOne(Field p_field, ArrayList<String> p_refListValue) {
		// TODO Auto-generated method stub
		String l_name = createName(p_field);

		Attribute l_att = new Attribute(l_name, p_refListValue);

		return l_att;
	}

	private String createName(Field p_field) {
		Column l_annot = p_field.getDeclaredAnnotation(Column.class);
		String l_name = null;
		if (l_annot != null)
			l_name = l_annot.name();
		if (l_name == null || l_name.isEmpty())
			l_name = p_field.getName();

		return l_name;
	}

	private Attribute createAttributeFromManyToMany(Field p_field) {

		String l_name = createName(p_field);

		Attribute l_att = null;
		if (p_field.getType() == String.class) {
			l_att = new Attribute(l_name, (ArrayList<String>) null);
		} else if (p_field.getType().isAssignableFrom(Number.class)) {
			l_att = new Attribute(l_name);
		}
		return l_att;
	}

	private Attribute createAttributeFromColumn(Field p_field) {
		// TODO Auto-generated method stub
		String l_name = createName(p_field);
		Attribute l_att = null;
		if (p_field.getType() == String.class) {
			l_att = new Attribute(l_name, (ArrayList<String>) null);
		} else if (p_field.getType().isAssignableFrom(Number.class)) {
			l_att = new Attribute(l_name);
		}
		return l_att;
	}

	public void ignoreFieldTypeOf(Class<?>... p_classes) {
		ignoreFieldTypeOf(Arrays.asList(p_classes));
	}

	public void ignoreFieldTypeOf(Collection<Class<?>> p_classes) {
		ignoreFieldsTypeOf.addAll(p_classes);
	}

}
