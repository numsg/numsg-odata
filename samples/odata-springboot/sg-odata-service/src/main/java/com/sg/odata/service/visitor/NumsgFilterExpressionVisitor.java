package com.sg.odata.service.visitor;

import com.sg.odata.service.util.NumsgEnumUtil;
import org.apache.olingo.commons.api.edm.EdmEnumType;
import org.apache.olingo.commons.api.edm.EdmType;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.commons.core.edm.primitivetype.*;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.uri.UriResource;
import org.apache.olingo.server.api.uri.UriResourceNavigation;
import org.apache.olingo.server.api.uri.UriResourcePrimitiveProperty;
import org.apache.olingo.server.api.uri.queryoption.expression.*;
import org.springframework.util.StringUtils;

import javax.persistence.EntityManagerFactory;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Created by shenhao on 2017/7/18.
 */
public class NumsgFilterExpressionVisitor implements ExpressionVisitor<Object> {
    private EntityManagerFactory entityManagerFactory;
    private CriteriaBuilder builder;
    private Root root;

    public NumsgFilterExpressionVisitor(CriteriaBuilder cb, Root rt, EntityManagerFactory entityManager) {
        this.builder = cb;
        this.root = rt;
        entityManagerFactory = entityManager;
    }

    @Override
    public Object visitMember(final Member member) throws ExpressionVisitException, ODataApplicationException {
        final List<UriResource> uriResourceParts = member.getResourcePath().getUriResourceParts();

        if (uriResourceParts.size() == 1 && uriResourceParts.get(0) instanceof UriResourcePrimitiveProperty) {
            UriResourcePrimitiveProperty uriResourceProperty = (UriResourcePrimitiveProperty) uriResourceParts.get(0);
            String fieldName = uriResourceProperty.getProperty().getName();
            return fieldName;
        } else if (uriResourceParts.get(0) instanceof UriResourceNavigation) {
            return getNavigationFilterFieldName(uriResourceParts);
        } else {
            throw new ODataApplicationException("Only primitive properties are implemented in filter expressions",
                    HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
        }
    }

    /**
     * 获取导航属性过滤的字段名称
     *
     * @param uriResourceParts
     * @return
     */
    private String getNavigationFilterFieldName(List<UriResource> uriResourceParts) {
        StringBuilder result = new StringBuilder();
        for (UriResource uriResource : uriResourceParts) {
            if (uriResource instanceof UriResourceNavigation) {
                UriResourceNavigation uriResourceNavigation = (UriResourceNavigation) uriResource;
                if (!StringUtils.isEmpty(result.toString())) {
                    result.append("/" + uriResourceNavigation.getProperty().getName());
                } else {
                    result.append(uriResourceNavigation.getProperty().getName());
                }
            } else if (uriResource instanceof UriResourcePrimitiveProperty) {
                UriResourcePrimitiveProperty uriResourcePrimitiveProperty = (UriResourcePrimitiveProperty) uriResource;
                if (!StringUtils.isEmpty(result)) {
                    result.append("/" + uriResourcePrimitiveProperty.getProperty().getName());
                } else {
                    result.append(uriResourcePrimitiveProperty.getProperty().getName());
                }
            }
        }
        return result.toString();
    }

    @Override
    public Object visitLiteral(Literal literal) throws ExpressionVisitException, ODataApplicationException {
        // String literals start and end with an single quotation mark

        String literalAsString = literal.getText();
        EdmType edmType = literal.getType();
        if (edmType instanceof EdmString) {

            String stringLiteral = "";
            if (literal.getText().length() > 2) {
                stringLiteral = literalAsString.substring(1, literalAsString.length() - 1);
            }
            Object enumName = NumsgEnumUtil.getEnum(stringLiteral);
            if (enumName != null) {
                return enumName;
            }
            return stringLiteral;
        }
        if (edmType instanceof EdmDateTimeOffset) {
            String dt = literalAsString.replace('T', ' ').replace('Z', ' ').trim();
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
            Date date;
            try {
                date = dateFormat.parse(dt);
            } catch (ParseException e) {
                throw new ODataApplicationException(e.getMessage(), HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
            }
            return date;
        }
        if (edmType instanceof EdmDouble || edmType instanceof EdmDecimal) {
            return Double.parseDouble(literalAsString);
        }
        if (edmType instanceof EdmBoolean) {
            String boolStr = literalAsString.toLowerCase();
            if (boolStr.contains("true") || boolStr.contains("false")) {
                return Boolean.parseBoolean(literalAsString);
            }
        }

        // Try to convert the literal into an Java Integer
        try {
            return Integer.parseInt(literalAsString);
        } catch (NumberFormatException e) {
            throw new ODataApplicationException("Only Edm.Int32 and Edm.String literals are implemented",
                    HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
        }
    }

    @Override
    public Object visitUnaryOperator(UnaryOperatorKind operator, Object operand)
            throws ExpressionVisitException, ODataApplicationException {
        if (operator == UnaryOperatorKind.NOT && operand instanceof Boolean) {
            // 1.) boolean negation
            return !(Boolean) operand;
        } else if (operator == UnaryOperatorKind.MINUS && operand instanceof Integer) {
            // 2.) arithmetic minus
            return -(Integer) operand;
        }

        throw new ODataApplicationException("Invalid type for unary operator",
                HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ENGLISH);
    }

    @Override
    public Object visitBinaryOperator(BinaryOperatorKind operator, Object left, Object right)
            throws ExpressionVisitException, ODataApplicationException {

        // Binary Operators are split up in three different kinds. Up to the kind of the operator it can be applied
        // to different types
        //   - Arithmetic operations like add, minus, modulo, etc. are allowed on numeric types like Edm.Int32
        //   - Logical operations are allowed on numeric types and also Edm.String
        //   - Boolean operations like and, or are allowed on Edm.Boolean
        // A detailed explanation can be found in OData Version 4.0 Part 2: URL Conventions

        if (operator == BinaryOperatorKind.ADD
                || operator == BinaryOperatorKind.MOD
                || operator == BinaryOperatorKind.MUL
                || operator == BinaryOperatorKind.DIV
                || operator == BinaryOperatorKind.SUB) {
            return evaluateArithmeticOperation(operator, left, right);
        } else if (operator == BinaryOperatorKind.EQ
                || operator == BinaryOperatorKind.NE
                || operator == BinaryOperatorKind.GE
                || operator == BinaryOperatorKind.GT
                || operator == BinaryOperatorKind.LE
                || operator == BinaryOperatorKind.LT) {
            return evaluateComparisonOperation(operator, left, right);
        } else if (operator == BinaryOperatorKind.AND
                || operator == BinaryOperatorKind.OR) {
            return evaluateBooleanOperation(operator, left, right);
        } else {
            throw new ODataApplicationException("Binary operation " + operator.name() + " is not implemented",
                    HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
        }
    }

    private Object evaluateBooleanOperation(BinaryOperatorKind operator, Object left, Object right)
            throws ODataApplicationException {
        if (left instanceof Predicate && right instanceof Predicate) {
            Predicate valueLeft = (Predicate) left;
            Predicate valueRight = (Predicate) right;
            // Than calculate the result value
            if (operator == BinaryOperatorKind.AND) {
                return builder.and(valueLeft, valueRight);
            } else {
                // OR
                return builder.or(valueLeft, valueRight);
            }
        } else {
            throw new ODataApplicationException("Boolean operations needs two numeric operands",
                    HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ENGLISH);
        }
    }

    private Object evaluateComparisonOperation(BinaryOperatorKind operator, Object left, Object right)
            throws ODataApplicationException {
        // All types in our tutorial supports all logical operations, but we have to make sure that the types are equals
        if (right instanceof Comparable) {

//            Comparable result;
//            if(left instanceof Integer) {
//                result = (Comparable<Integer>)right;
//            } else if(left instanceof String) {
//                result = (Comparable<String>)right;
//            } else if(left instanceof Boolean) {
//                result = (Comparable<Boolean>)right;
//            } else {
//                result = (Comparable)right;
//            }
            Comparable result = (Comparable) right;

            if (javax.persistence.criteria.Expression.class.isAssignableFrom(left.getClass())) {
                javax.persistence.criteria.Expression leftExp = (javax.persistence.criteria.Expression) left;
                if (operator == BinaryOperatorKind.EQ) {
                    return builder.equal(leftExp, right);
                } else if (operator == BinaryOperatorKind.NE) {
                    return builder.notEqual(leftExp, right);
                } else if (operator == BinaryOperatorKind.GE) {
                    return builder.greaterThanOrEqualTo(leftExp, result);
                } else if (operator == BinaryOperatorKind.GT) {
                    return builder.greaterThan(leftExp, result);
                } else if (operator == BinaryOperatorKind.LE) {
                    return builder.lessThanOrEqualTo(leftExp, result);
                } else {
                    // BinaryOperatorKind.LT
                    return builder.lessThan(leftExp, result);
                }
            } else {
                String leftStr = left.toString();
                if (operator == BinaryOperatorKind.EQ) {
                    return builder.equal(getPath(leftStr), right);
                } else if (operator == BinaryOperatorKind.NE) {
                    return builder.notEqual(getPath(leftStr), right);
                } else if (operator == BinaryOperatorKind.GE) {
                    return builder.greaterThanOrEqualTo(getPath(leftStr), result);
                } else if (operator == BinaryOperatorKind.GT) {
                    return builder.greaterThan(getPath(leftStr), result);
                } else if (operator == BinaryOperatorKind.LE) {
                    return builder.lessThanOrEqualTo(getPath(leftStr), result);
                } else {
                    // BinaryOperatorKind.LT
                    return builder.lessThan(getPath(leftStr), result);
                }
            }
        } else {
            throw new ODataApplicationException("Comparision needs two equal types",
                    HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ENGLISH);
        }
    }

    private Path getPath(String leftStr) {
        if (leftStr.indexOf("/") > 0) {
            List<String> propertys = Arrays.asList(leftStr.split("/"));
            Path getString = root.get(propertys.get(0));
            for (int i = 1; i < propertys.size(); i++) {
                getString = getString.get(propertys.get(i));
            }
            return getString;
        } else {
            return root.get(leftStr);
        }
    }

    private Object evaluateArithmeticOperation(BinaryOperatorKind operator, Object left,
                                               Object right) throws ODataApplicationException {
        // First check if the type of both operands is numerical
        if (left instanceof Integer && right instanceof Integer) {
            Integer valueLeft = (Integer) left;
            Integer valueRight = (Integer) right;

            // Than calculate the result value
            if (operator == BinaryOperatorKind.ADD) {
                return valueLeft + valueRight;
            } else if (operator == BinaryOperatorKind.SUB) {
                return valueLeft - valueRight;
            } else if (operator == BinaryOperatorKind.MUL) {
                return valueLeft * valueRight;
            } else if (operator == BinaryOperatorKind.DIV) {
                return valueLeft / valueRight;
            } else {
                // BinaryOperatorKind,MOD
                return valueLeft % valueRight;
            }
        } else {
            throw new ODataApplicationException("Arithmetic operations needs two numeric operands",
                    HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ENGLISH);
        }
    }

    @Override
    public Object visitMethodCall(MethodKind methodCall, List<Object> parameters)
            throws ExpressionVisitException, ODataApplicationException {
        if (methodCall == MethodKind.CONTAINS || methodCall == MethodKind.STARTSWITH || methodCall == MethodKind.ENDSWITH) {
            String valueParam2 = (String) parameters.get(1);
            String likeParam = "%" + valueParam2 + "%";
            switch (methodCall) {
                case STARTSWITH:
                    likeParam = valueParam2 + "%";
                    break;
                case ENDSWITH:
                    likeParam = "%" + valueParam2;
                    break;
            }
            if (parameters.get(0) instanceof String) {
                String valueParam1 = (String) parameters.get(0);
                return builder.like(getPath(valueParam1), likeParam);
            } else {
                javax.persistence.criteria.Expression valueParam1 = (javax.persistence.criteria.Expression) parameters.get(0);
                return builder.like(valueParam1, likeParam);
//                throw new ODataApplicationException("Contains needs two parametes of type Edm.String",
//                        HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ENGLISH);
            }
        }

        if (methodCall == MethodKind.TOLOWER) {
            String valueParam1 = (String) parameters.get(0);
            return builder.lower(root.get(valueParam1));
        }

        if (methodCall == MethodKind.TOUPPER) {
            String valueParam1 = (String) parameters.get(0);
            return builder.upper(root.get(valueParam1));
        }


//        if (methodCall == MethodKind.DATE) {
//            String valueParam1 = (String) parameters.get(0);
//        }
        throw new ODataApplicationException("Method call " + methodCall + " not implemented",
                HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
    }


    @Override
    public Object visitTypeLiteral(EdmType type) throws ExpressionVisitException, ODataApplicationException {
        throw new ODataApplicationException("Type literals are not implemented",
                HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
    }

    @Override
    public Object visitAlias(String aliasName) throws ExpressionVisitException, ODataApplicationException {
        throw new ODataApplicationException("Aliases are not implemented",
                HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
    }

    @Override
    public Object visitEnum(EdmEnumType type, List<String> enumValues)
            throws ExpressionVisitException, ODataApplicationException {
        throw new ODataApplicationException("Enums are not implemented",
                HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
    }

    @Override
    public Object visitLambdaExpression(String lambdaFunction, String lambdaVariable, Expression expression)
            throws ExpressionVisitException, ODataApplicationException {
        throw new ODataApplicationException("Lamdba expressions are not implemented",
                HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
    }

    @Override
    public Object visitLambdaReference(String variableName)
            throws ExpressionVisitException, ODataApplicationException {
        throw new ODataApplicationException("Lamdba references are not implemented",
                HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
    }
}