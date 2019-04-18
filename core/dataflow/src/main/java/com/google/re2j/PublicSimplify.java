package com.google.re2j;

/**
 * Wrapper class of re2j.Simplify.<!-- --> This class simplifies a Regexp
 * generated by the Parser.<br>
 * 
 * @author Zuozhi Wang
 *
 */
public class PublicSimplify {

    /**
     * This method applies various simplifications to a given Regexp, and
     * converts it to an equivalent Regexp. <br>
     * Regexp is generated by PublicParser. <br>
     * For example, "x{1,2}" will be simplified to "xx?" <br>
     * 
     * @param re,
     *            regexp needs to be simplified.
     * @return publicRegexp, regexp after simplification.
     */
    public static PublicRegexp simplify(Regexp re) {
        return PublicRegexp.deepCopy(Simplify.simplify(re));
    }

}
