package org.weka.jpa.utils;

/**
 * A interface CallbackField define o formato do callback (lambda) que será
 * usado para processamento especializado de certos campos.
 * 
 * {@link CallbackField} é definida com dois campos genéricos:
 * 
 * O primeiro (R) o tipo do valor que será retornado, obrigatóriamente
 * {@link String} ou algum tipo de {@link Number},
 * 
 * o segundo (V) genérico é o tipo de objeto que o {@link CallbackField} irá
 * manipular no método {@link CallbackField#call(String, V)}.
 * 
 * 
 * @author Carlos Delfino {consultoria@carlosdelfino.eti.br, Aminadabe B. Souza
 *         {aminadabebs@gmail.com} e Carlos Barros {carlos.barros22@gmail.com}
 *
 * @param <R>
 *            Tipo do retorno, obrigatório Number ou String
 * @param <V>
 *            Tipo do valor referente ao campo
 */
@FunctionalInterface
public interface CallbackField<R> {

	/**
	 * O método {@link #call(String, V)} é chamado quando necessário manipular o
	 * campo no qual este {@link CallbackField} foi registrado este método
	 * recebe o nome do campo que forneceu o valor e o valor que deverá ser
	 * manipullado, que será do tipo V (Generic).
	 * 
	 * 
	 * @param p_entity
	 *            Entidade a qual pertence o campo e o valor obtido no campo.
	 * @param p_fieldName
	 *            Nome do campo que está fornecendo o valor
	 * @param p_fieldValue
	 *            Valor do campo a ser manipulado
	 * 
	 * @return um valor do tipo R que deve ser {@link String} ou um tipo
	 *         {@link Number}
	 */
	public R call(Object p_entity, String p_fieldName, Object p_fieldValue);
}
