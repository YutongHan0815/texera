package edu.uci.ics.texera.dataflow.regexmatcher;

import com.google.re2j.PublicParser;
import com.google.re2j.PublicRE2;
import com.google.re2j.PublicRegexp;
import com.google.re2j.PublicSimplify;

/**
 * This class translates a regex to a boolean query of n-grams, according to the
 * <a href='https://swtch.com/~rsc/regexp/regexp4.html'>algorithm</a> described
 * in Russ Cox's article. <br>
 * 
 * @Author Zuozhi Wang
 * @Author Shuying Lai
 * 
 */
public class RegexToGramQueryTranslator {
    /**
     * This method translates a regular expression to a boolean expression of
     * n-grams. (default is TranslatorUtils.DEFAULT_GRAM_LENGTH, which is 3)
     * <br>
     * Then the boolean expression can be queried using a n-gram inverted index
     * to speed up regex matching. <br>
     * 
     * 
     * @param regex,
     *            the regex string to be translated.
     * @return GamBooleanQeruy, a boolean query of n-grams.
     */
    public static GramBooleanQuery translate(String regex) throws com.google.re2j.PatternSyntaxException {

        return translate(regex, TranslatorUtils.DEFAULT_GRAM_LENGTH);
    }

    /**
     * This method translates a regular expression to a boolean expression of a
     * custom gram length. <br>
     * 
     * @param regex,
     *            the regex string to be translated.
     * @return GamBooleanQeruy, a boolean query of n-grams.
     */
    public static GramBooleanQuery translate(String regex, int gramLength)
            throws com.google.re2j.PatternSyntaxException {

        TranslatorUtils.GRAM_LENGTH = gramLength;

        // Since the inverted index relies on lower-case grams, we need to
        // convert the characters to lower case.
        regex = regex.toLowerCase();
        
        PublicRegexp re = PublicParser.parse(regex, PublicRE2.PERL);
        re = PublicSimplify.simplify(re);

        RegexInfo regexInfo = analyze(re);
        regexInfo.simplify(true);

        TranslatorUtils.GRAM_LENGTH = TranslatorUtils.DEFAULT_GRAM_LENGTH;

        TranslatorUtils.escapeSpecialCharacters(regexInfo.match);

        return regexInfo.match;
    }

    /**
     * This is the main function of analyzing a regular expression. <br>
     * This methods walks through the regex abstract syntax tree generated by
     * RE2J, and return a {@code RegexInfo} object for given regex.
     * 
     * @param PublicRegexp
     * @return RegexInfo
     */
    private static RegexInfo analyze(PublicRegexp re) {
        RegexInfo info = new RegexInfo();
        // FOLD_CASE means case insensitive
        boolean isCaseSensitive = (re.getFlags() & PublicRE2.FOLD_CASE) != PublicRE2.FOLD_CASE;
        switch (re.getOp()) {
        // NO_MATCH is a regex that doesn't match anything.
        // It's used to handle error cases, which shouldn't
        // happen unless something goes wrong.
        case NO_MATCH:
        case VERTICAL_BAR:
        case LEFT_PAREN: {
            return RegexInfo.matchNone();
        }
        // The following cases are treated as
        // a regex that matches an empty string.
        case EMPTY_MATCH:
        case WORD_BOUNDARY:
        case NO_WORD_BOUNDARY:
        case BEGIN_LINE:
        case END_LINE:
        case BEGIN_TEXT:
        case END_TEXT: {
            return RegexInfo.emptyString();
        }
        // A regex that matches any character
        case ANY_CHAR:
        case ANY_CHAR_NOT_NL: {
            return RegexInfo.anyChar();
        }
        // regexp1 | regexp2
        case ALTERNATE:
            return fold((x, y) -> alternate(x, y), re.getSubs(), RegexInfo.matchAny());
        // regexp1 regexp2
        case CONCAT:
            return fold((x, y) -> concat(x, y), re.getSubs(), RegexInfo.matchNone());
        // (regexp1)
        case CAPTURE:
            return analyze(re.getSubs()[0]).simplify(false);
        // [a-z]
        case CHAR_CLASS:
            if (re.getRunes().length == 0) {
                return RegexInfo.matchNone();
            } else if (re.getRunes().length == 1) {
                String exactStr;
                if (isCaseSensitive) {
                    exactStr = Character.toString((char) re.getRunes()[0]);
                } else {
                    exactStr = Character.toString((char) re.getRunes()[0]).toLowerCase();
                }
                info.exact.add(exactStr);
                info.simplify(false);
                return info;
            }
            // convert all runes to lower case if not case sensitive
            if (!isCaseSensitive) {
                for (int i = 0; i < re.getRunes().length; i++) {
                    re.getRunes()[i] = Character.toLowerCase(re.getRunes()[i]);
                }
            }
            // add characters between two runes to exact
            int count = 0;
            for (int i = 0; i < re.getRunes().length; i += 2) {
                count += re.getRunes()[i + 1] - re.getRunes()[i];
                // If the class is too large, it's okay to overestimate.
                if (count > 100) {
                    return RegexInfo.matchAny();
                }

                for (int codePoint = re.getRunes()[i]; codePoint <= re.getRunes()[i + 1]; codePoint++) {
                    info.exact.add(Character.toString((char) codePoint));
                }
            }
            info.simplify(false);
            return info;
        // abcd
        case LITERAL:
            if (re.getRunes().length == 0) {
                return RegexInfo.emptyString();
            }
            // convert runes to string
            String literal = "";
            if (isCaseSensitive) { // case sensitive
                for (int rune : re.getRunes()) {
                    literal += Character.toString((char) rune);
                }
            } else {
                for (int rune : re.getRunes()) {
                    literal += Character.toString((char) rune).toLowerCase();
                }
            }
            info = new RegexInfo();
            info.exact.add(literal);
            info.simplify(false);
            return info;
        // regexp{min,max} (repeat at least min times, at most max times)
        case REPEAT:
            // When min is zero, we treat REPEAT as STAR
            // When min is greater than zero, we treat REPEAT as PLUS, and let
            // it fall through.
            if (re.getMin() == 0) {
                return RegexInfo.matchAny();
            }
            // !!!!! intentionally FALL THROUGH to PLUS !!!!!
            // regexp+ (repeat one more more times)
        case PLUS:
            // The regexInfo of "(expr)+" should be the same as the info of
            // "expr",
            // except that "exact" is null, because we don't know the number of
            // repetitions.
            info = analyze(re.getSubs()[0]);
            if (!info.exact.isEmpty()) {
                info.prefix.addAll(info.exact);
                info.suffix.addAll(info.exact);
                info.exact.clear();
            }
            return info.simplify(false);
        // regexp? (repeat zero or one time)
        case QUEST:
            // The regexInfo of "(expr)?" shoud be either the same as the info
            // of "expr",
            // or the same as the info of an empty string.
            return alternate(analyze(re.getSubs()[0]), RegexInfo.emptyString());
        // regexp* (repeat zero or more tims)
        case STAR:
            return RegexInfo.matchAny();
        default:
            return RegexInfo.matchAny();
        }
    }

    /**
     * This function calculates the {@code RegexInfo} of alternation of two
     * given {@code RegexInfo}
     * 
     * @param xInfo
     * @param yInfo
     * @return xyInfo
     */
    private static RegexInfo alternate(RegexInfo xInfo, RegexInfo yInfo) {
        RegexInfo xyInfo = new RegexInfo();

        if (!xInfo.exact.isEmpty() && !yInfo.exact.isEmpty()) {
            xyInfo.exact = TranslatorUtils.union(xInfo.exact, yInfo.exact, false);
        } else if (!xInfo.exact.isEmpty()) {
            xyInfo.prefix = TranslatorUtils.union(xInfo.exact, yInfo.prefix, false);
            xyInfo.suffix = TranslatorUtils.union(xInfo.exact, yInfo.suffix, true);
            xInfo.match = GramBooleanQuery.combine(xInfo.match, xInfo.exact);
        } else if (!yInfo.exact.isEmpty()) {
            xyInfo.prefix = TranslatorUtils.union(xInfo.prefix, yInfo.exact, false);
            xyInfo.suffix = TranslatorUtils.union(xInfo.suffix, yInfo.exact, true);
            yInfo.match = GramBooleanQuery.combine(yInfo.match, yInfo.exact);
        } else {
            xyInfo.prefix = TranslatorUtils.union(xInfo.prefix, yInfo.prefix, false);
            xyInfo.suffix = TranslatorUtils.union(xInfo.suffix, yInfo.suffix, true);
        }

        xyInfo.emptyable = xInfo.emptyable || yInfo.emptyable;

        xyInfo.match = GramBooleanQuery.computeDisjunction(xInfo.match, yInfo.match);

        xyInfo.simplify(false);
        return xyInfo;
    }

    /**
     * This function calculates the {@code RegexInfo} of concatenation of two
     * given {@code RegexInfo}
     * 
     * @param xInfo
     * @param yInfo
     * @return xyInfo
     */
    private static RegexInfo concat(RegexInfo xInfo, RegexInfo yInfo) {
        RegexInfo xyInfo = new RegexInfo();

        xyInfo.match = GramBooleanQuery.computeConjunction(xInfo.match, yInfo.match);

        if (!xInfo.exact.isEmpty() && !yInfo.exact.isEmpty()) {
            xyInfo.exact = TranslatorUtils.cartesianProduct(xInfo.exact, yInfo.exact, false);
        } else {
            if (!xInfo.exact.isEmpty()) {
                xyInfo.prefix = TranslatorUtils.cartesianProduct(xInfo.exact, yInfo.prefix, false);
            } else {
                xyInfo.prefix = xInfo.prefix;
                if (xInfo.emptyable) {
                    xyInfo.prefix = TranslatorUtils.union(xyInfo.prefix, yInfo.prefix, false);
                }
            }

            if (!yInfo.exact.isEmpty()) {
                xyInfo.suffix = TranslatorUtils.cartesianProduct(xInfo.suffix, yInfo.exact, true);
            } else {
                xyInfo.suffix = yInfo.suffix;
                if (yInfo.emptyable) {
                    xyInfo.suffix = TranslatorUtils.union(xyInfo.suffix, xInfo.suffix, true);
                }
            }
        }

        if (xInfo.exact.isEmpty() && yInfo.exact.isEmpty() && xInfo.suffix.size() <= TranslatorUtils.MAX_SET_SIZE
                && yInfo.prefix.size() <= TranslatorUtils.MAX_SET_SIZE && TranslatorUtils.minLenOfString(xInfo.suffix)
                        + TranslatorUtils.minLenOfString(yInfo.prefix) >= TranslatorUtils.GRAM_LENGTH) {

            xyInfo.match = GramBooleanQuery.combine(xyInfo.match,
                    TranslatorUtils.cartesianProduct(xInfo.suffix, yInfo.prefix, false));
        }

        xyInfo.simplify(false);

        return xyInfo;
    }

    /**
     * This function takes an array of regex, analyzes each one, and folds them
     * one by one with a given function (either concat or alternate).
     * 
     * @param iFold
     * @param subExpressions
     * @param zero,
     *            returned when the array of regex is empty
     * @return
     */
    private static RegexInfo fold(TranslatorUtils.IFold iFold, PublicRegexp[] subExpressions, RegexInfo zero) {
        if (subExpressions.length == 0) {
            return zero;
        } else if (subExpressions.length == 1) {
            return analyze(subExpressions[0]);
        }

        RegexInfo info = iFold.foldFunc(analyze(subExpressions[0]), analyze(subExpressions[1]));
        for (int i = 2; i < subExpressions.length; i++) {
            info = iFold.foldFunc(info, analyze(subExpressions[i]));
        }
        return info;
    }

}
