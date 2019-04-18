package edu.uci.ics.texera.textql.statements;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.builder.EqualsBuilder;

import edu.uci.ics.texera.dataflow.common.PredicateBase;
import edu.uci.ics.texera.dataflow.plangen.OperatorLink;
import edu.uci.ics.texera.textql.planbuilder.beans.PassThroughPredicate;

/**
 * Object representation of a "CREATE VIEW ..." statement.
 * 
 * @author Flavio Bayer
 *
 */
public class CreateViewStatement extends Statement {
    
    /**
     * The statement to which the { @code CreateViewStatement } creates an alias for.
     * e.g. in "CREATE VIEW v AS SELECT * FROM t"; the view with ID 'v' will have the
     * select statement "SELECT * FROM t" as sub-statement (in a SlectExtractStatement
     * object).
     */
    private Statement subStatement;
      
    /**
     * Create a { @code CreateViewStatement } with the parameters set to { @code null }
     */
    public CreateViewStatement() {
        this(null, null);
    }
    
    /**
     * Create a { @code CreateViewStatement } with the given parameters.
     * @param id The ID of this statement.
     * @param subStatement The subStatement of this statement.
     */
    public CreateViewStatement(String id, Statement subStatement) {
        super(id);
        this.subStatement = subStatement;
    }
    
    
    /**
     * Get the sub-statement of this statement.
     * @return The sub-statement of this statement.
     */
    public Statement getSubStatement() {
        return subStatement;
    }
    
    /**
     * Set the sub-statement of the statement.
     * @param subStatement The new sub-statement to set.
     */
    public void setSubStatement(Statement subStatement) {
        this.subStatement = subStatement;
    }
    
    @Override
    public String getInputNodeID(){
        return getId();
    }

    @Override
    public String getOutputNodeID(){
        return getId();
    }
    
    /**
     * Return a list of operators generated when this statement is converted to beans.
     * The { @code CreateViewStatement } generate a { @code PassThroughBean }.
     * @return The list of operator beans generated by this statement.
     */
    @Override
    public List<PredicateBase> getInternalOperatorBeans(){
        return Arrays.asList(new PassThroughPredicate(this.getId()));
    }
    
    /**
     * Return a list of links generated when this statement is converted to beans.
     * The { @code CreateViewStatement } generate no internal links, an empty list is returned.
     * @return The list of link beans generated by this statement.
     */
    @Override
    public List<OperatorLink> getInternalLinkBeans(){
        return Collections.emptyList();
    }
     
    /**
     * RReturn a list of IDs of operators required by this statement (the dependencies of this Statement)
     * when converted to beans.
     * The { @code CreateViewStatement } has only its subStatement as required view.
     * @return A list with the IDs of required Statements
     */
    @Override
    public List<String> getInputViews(){
        return Arrays.asList(subStatement.getId());
    }
    
    
    @Override
    public boolean equals(Object obj) {
        if (obj == null) { return false; }
        if (obj.getClass() != this.getClass()) { return false; }
        CreateViewStatement otherCreateViewStatement = (CreateViewStatement) obj;
        return new EqualsBuilder()
                    .appendSuper(super.equals(otherCreateViewStatement))
                    .append(subStatement, otherCreateViewStatement.subStatement)
                    .isEquals();
    }
    
}