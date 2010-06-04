/*
 * Copyright (c) 2010.  The Broad Institute
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED 'AS IS', WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package org.broadinstitute.sting.gatk.contexts.variantcontext;

import java.util.*;
import org.apache.commons.jexl.*;
import org.broadinstitute.sting.utils.StingException;
import org.broadinstitute.sting.utils.GenomeLoc;
import org.broadinstitute.sting.utils.genotype.HardyWeinbergCalculation;
import org.broad.tribble.vcf.VCFRecord;

public class VariantContextUtils {
    /** 
     * A simple but common wrapper for matching VariantContext objects using JEXL expressions
     */
    public static class JexlVCMatchExp {
        public String name;
        public Expression exp;

        /**
         * Create a new matcher expression with name and JEXL expression exp
         * @param name name
         * @param exp  expression
         */
        public JexlVCMatchExp(String name, Expression exp) {
            this.name = name;
            this.exp = exp;
        }
    }

    /**
     * Method for creating JexlVCMatchExp from input walker arguments names and exps.  These two arrays contain
     * the name associated with each JEXL expression. initializeMatchExps will parse each expression and return
     * a list of JexlVCMatchExp, in order, that correspond to the names and exps.  These are suitable input to
     * match() below.
     *
     * @param names names
     * @param exps  expressions
     * @return list of matches
     */
    public static List<JexlVCMatchExp> initializeMatchExps(String[] names, String[] exps) {
        if ( names == null || exps == null )
            throw new StingException("BUG: neither names nor exps can be null: names " + names + " exps=" + exps );

        if ( names.length != exps.length )
            throw new StingException("Inconsistent number of provided filter names and expressions: names=" + names + " exps=" + exps);

        Map<String, String> map = new HashMap<String, String>();
        for ( int i = 0; i < names.length; i++ ) { map.put(names[i], exps[i]); }

        return VariantContextUtils.initializeMatchExps(map);
    }

    /**
     * Method for creating JexlVCMatchExp from input walker arguments mapping from names to exps.  These two arrays contain
     * the name associated with each JEXL expression. initializeMatchExps will parse each expression and return
     * a list of JexlVCMatchExp, in order, that correspond to the names and exps.  These are suitable input to
     * match() below.
     *
     * @param names_and_exps mapping of names to expressions
     * @return list of matches
     */
    public static List<JexlVCMatchExp> initializeMatchExps(Map<String, String> names_and_exps) {
        List<JexlVCMatchExp> exps = new ArrayList<JexlVCMatchExp>();

        for ( Map.Entry<String, String> elt : names_and_exps.entrySet() ) {
            String name = elt.getKey();
            String expStr = elt.getValue();

            if ( name == null || expStr == null ) throw new IllegalArgumentException("Cannot create null expressions : " + name +  " " + expStr);
            try {
                Expression exp = ExpressionFactory.createExpression(expStr);
                exps.add(new JexlVCMatchExp(name, exp));
            } catch (Exception e) {
                throw new StingException("Invalid expression used (" + expStr + "). Please see the JEXL docs for correct syntax.");
            }
        }

        return exps;
    }

    /**
     * Returns true if exp match VC.  See collection<> version for full docs.
     * @param vc    variant context
     * @param exp   expression
     * @return true if there is a match
     */
    public static boolean match(VariantContext vc, JexlVCMatchExp exp) {
        return match(vc,Arrays.asList(exp)).get(exp);
    }

    /**
     * Matches each JexlVCMatchExp exp against the data contained in vc, and returns a map from these
     * expressions to true (if they matched) or false (if they didn't).  This the best way to apply JEXL
     * expressions to VariantContext records.  Use initializeMatchExps() to create the list of JexlVCMatchExp
     * expressions.
     *
     * @param vc   variant context
     * @param exps expressions
     * @return true if there is a match
     */
    public static Map<JexlVCMatchExp, Boolean> match(VariantContext vc, Collection<JexlVCMatchExp> exps) {
        return new VariantJEXLContext(exps,vc).getVars();

    }

    /**
     * Returns true if exp match VC.  See collection<> version for full docs.
     * @param g    genotype
     * @param exp   expression
     * @return true if there is a match
     */
    public static boolean match(Genotype g, JexlVCMatchExp exp) {
        return match(g,Arrays.asList(exp)).get(exp);
    }

    /**
     * Matches each JexlVCMatchExp exp against the data contained in vc, and returns a map from these
     * expressions to true (if they matched) or false (if they didn't).  This the best way to apply JEXL
     * expressions to VariantContext records.  Use initializeMatchExps() to create the list of JexlVCMatchExp
     * expressions.
     *
     * @param g    genotype
     * @param exps expressions
     * @return true if there is a match
     */
    public static Map<JexlVCMatchExp, Boolean> match(Genotype g, Collection<JexlVCMatchExp> exps) {
        return new VariantJEXLContext(exps,g).getVars();

    }

    private static void addAttributesToMap(Map<String, String> infoMap, Map<String, ?> attributes, String prefix ) {
        for (Map.Entry<String, ?> e : attributes.entrySet()) {
            infoMap.put(prefix + String.valueOf(e.getKey()), String.valueOf(e.getValue()));
        }
    }

    public static double computeHardyWeinbergPvalue(VariantContext vc) {
        if ( vc.getChromosomeCount() == 0 )
            return 0.0;
        return HardyWeinbergCalculation.hwCalculate(vc.getHomRefCount(), vc.getHetCount(), vc.getHomVarCount());
    }


    public static VariantContext simpleMerge(Set<VariantContext> VCs) {
        if ( VCs == null || VCs.size() == 0 )
            return null;

        Iterator<VariantContext> iter = VCs.iterator();

        // establish the baseline info from the first VC
        VariantContext first = iter.next();
        String name = first.getName();
        GenomeLoc loc = first.getLocation();
        Set<Allele> alleles = new HashSet<Allele>(first.getAlleles());
        Map<String, Genotype> genotypes = new HashMap<String, Genotype>(first.getGenotypes());
        double negLog10PError = first.isVariant() ? first.getNegLog10PError() : -1;
        Set<String> filters = new HashSet<String>(first.getFilters());
        Map<String, String> attributes = new HashMap<String, String>();
        int depth = 0;
        if ( first.hasAttribute(VCFRecord.DEPTH_KEY) )
            depth = Integer.valueOf(first.getAttribute(VCFRecord.DEPTH_KEY).toString());

        // cycle through and add info from the other VCs, making sure the loc/reference matches
        while ( iter.hasNext() ) {
            VariantContext vc = iter.next();
            if ( !loc.equals(vc.getLocation()) || !first.getReference().equals(vc.getReference()) )
                return null;

            alleles.addAll(vc.getAlleles());
            genotypes.putAll(vc.getGenotypes());

            negLog10PError = Math.max(negLog10PError, vc.isVariant() ? vc.getNegLog10PError() : -1);

            filters.addAll(vc.getFilters());
            if ( vc.hasAttribute(VCFRecord.DEPTH_KEY) )
                depth += Integer.valueOf(vc.getAttribute(VCFRecord.DEPTH_KEY).toString());
        }

        if ( depth > 0 )
            attributes.put(VCFRecord.DEPTH_KEY, String.valueOf(depth));
        return new VariantContext(name, loc, alleles, genotypes, negLog10PError, filters, attributes);
    }
}