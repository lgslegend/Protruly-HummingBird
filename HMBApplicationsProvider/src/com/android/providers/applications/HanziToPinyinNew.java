package com.android.providers.applications;

import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import libcore.icu.Transliterator;
/**
 * Created by lijun on 17-6-23.
 */

public class HanziToPinyinNew {
    private static final String TAG = "HanziToPinyinNew";

    private static HanziToPinyinNew sInstance;
    private Transliterator mPinyinTransliterator;
    private Transliterator mAsciiTransliterator;

    public static Map<String,Character> specialHanzi = new HashMap<String,Character>();
    static {
        specialHanzi.put("4e50", '\u53fb');
        specialHanzi.put("6c88", '\u9648');
        specialHanzi.put("66fe", '\u5c42');
        specialHanzi.put("62d7", '\u725b');
        specialHanzi.put("963f", '\u9e45');
        specialHanzi.put("814c", '\u554a');
        specialHanzi.put("62d4", '\u722c');
        specialHanzi.put("8584", '\u64ad');
        specialHanzi.put("5821", '\u5e03');
        specialHanzi.put("5821", '\u94fa');
        specialHanzi.put("66b4", '\u94fa');
        specialHanzi.put("81c2", '\u80cc');
        specialHanzi.put("8f9f", '\u7b14');
        specialHanzi.put("6241", '\u504f');
        specialHanzi.put("4fbf", '\u504f');
        specialHanzi.put("8180", '\u65c1');
        specialHanzi.put("78c5", '\u65c1');
        specialHanzi.put("9aa0", '\u6f02');
        specialHanzi.put("5c4f", '\u51b0');
        specialHanzi.put("5265", '\u5305');
        specialHanzi.put("6cca", '\u535a');
        specialHanzi.put("4f2f", '\u62dc');
        specialHanzi.put("535c", '\u535a');
        specialHanzi.put("5426", '\u5c41');
        specialHanzi.put("812f", '\u798f');
        specialHanzi.put("5f04", '\u9686');
        specialHanzi.put("759f", '\u836f');
        specialHanzi.put("5a1c", '\u8bfa');
        specialHanzi.put("8feb", '\u724c');
        specialHanzi.put("5228", '\u5305');
        specialHanzi.put("70ae", '\u5305');
        specialHanzi.put("7011", '\u5305');
        specialHanzi.put("66dd", '\u5305');
        specialHanzi.put("5f3a", '\u964d');
        specialHanzi.put("6816", '\u606f');
        specialHanzi.put("8e4a", '\u606f');
        specialHanzi.put("7a3d", '\u671f');
        specialHanzi.put("8368", '\u94b1');
        specialHanzi.put("8d84", '\u59be');
        specialHanzi.put("96c0", '\u6572');
        specialHanzi.put("5708", '\u5a1f');
        specialHanzi.put("4eb2", '\u8bf7');
        specialHanzi.put("8272", '\u6652');
        specialHanzi.put("585e", '\u6da9');
        specialHanzi.put("53a6", '\u590f');
        specialHanzi.put("6c64", '\u5546');
        specialHanzi.put("6298", '\u54f2');
        specialHanzi.put("62fe", '\u86c7');
        specialHanzi.put("8bc6", '\u81f4');
        specialHanzi.put("4f3c", '\u6b7b');
        specialHanzi.put("5c5e", '\u4e3b');
        specialHanzi.put("845a", '\u795e');
        specialHanzi.put("4ec0", '\u5e02');
        specialHanzi.put("719f", '\u624b');
        specialHanzi.put("8bf4", '\u7761');
        specialHanzi.put("6570", '\u70c1');
        specialHanzi.put("5fea", '\u949f');
        specialHanzi.put("5bbf", '\u4f11');
        specialHanzi.put("6c93", '\u5854');
        specialHanzi.put("8c03", '\u8df3');
        specialHanzi.put("892a", '\u541e');
        specialHanzi.put("62d3", '\u62d6');
        specialHanzi.put("5729", '\u865a');
        specialHanzi.put("5c3e", '\u4ee5');
        specialHanzi.put("5c09", '\u5582');
        specialHanzi.put("9057", '\u5582');

    }
    public static class Token {
        /**
         * Separator between target string for each source char
         */
        public static final String SEPARATOR = " ";

        public static final int LATIN = 1;
        public static final int PINYIN = 2;
        public static final int UNKNOWN = 3;

        /**
         * M: The following lines are provided and maintained by Mediatek Inc.
         * New Feature ALPS00338325. @{
         */
        public static final int RUSSIAN = 14;
        public static final int ARABIC = 15;
        public static final int HEBREW = 16;
        /** @} */

        public Token() {
        }

        public Token(int type, String source, String target) {
            this.type = type;
            this.source = source;
            this.target = target;
        }

        /**
         * Type of this token, ASCII, PINYIN or UNKNOWN.
         */
        public int type;
        /**
         * Original string before translation.
         */
        public String source;
        /**
         * Translated string of source. For Han, target is corresponding Pinyin. Otherwise target is
         * original string in source.
         */
        public String target;
    }

    private HanziToPinyinNew() {
        try {
            mPinyinTransliterator = new Transliterator("Han-Latin/Names; Latin-Ascii; Any-Upper");
            mAsciiTransliterator = new Transliterator("Latin-Ascii");
        } catch (RuntimeException e) {
            Log.w(TAG, "Han-Latin/Names transliterator data is missing,"
                    + " HanziToPinyinNew is disabled");
        }
    }

    public boolean hasChineseTransliterator() {
        return mPinyinTransliterator != null;
    }

    public static HanziToPinyinNew getInstance() {
        synchronized (HanziToPinyinNew.class) {
            if (sInstance == null) {
                sInstance = new HanziToPinyinNew();
            }
            return sInstance;
        }
    }

    private void tokenize(char character, Token token) {
        token.source = Character.toString(character);

        // ASCII
        if (character < 128) {
            token.type = Token.LATIN;
            token.target = token.source;
            return;
        }

        // Extended Latin. Transcode these to ASCII equivalents
        if (character < 0x250 || (0x1e00 <= character && character < 0x1eff)) {
            token.type = Token.LATIN;
            token.target = mAsciiTransliterator == null ? token.source :
                    mAsciiTransliterator.transliterate(token.source);
            return;
        }

        token.type = Token.PINYIN;
        token.target = mPinyinTransliterator.transliterate(token.source);
        if (TextUtils.isEmpty(token.target) ||
                TextUtils.equals(token.source, token.target)) {
            token.type = Token.UNKNOWN;
            token.target = token.source;
        }
    }
    private void tokenizeForSpecialHanzi(char character, Token token) {
        if (specialHanzi.get(Integer.toHexString(character)) != null) {
            character = specialHanzi.get(Integer.toHexString(character));
            Log.w(TAG,"convert to character = "+character);
        }
        token.source = Character.toString(character);
        token.type = Token.PINYIN;
        token.target = mPinyinTransliterator.transliterate(token.source);
        if (TextUtils.isEmpty(token.target) ||
                TextUtils.equals(token.source, token.target)) {
            token.type = Token.UNKNOWN;
            token.target = token.source;
        }
    }
    public String transliterate(final String input) {
        if (!hasChineseTransliterator() || TextUtils.isEmpty(input)) {
            return null;
        }
        return mPinyinTransliterator.transliterate(input);
    }

    /**
     * Convert the input to a array of tokens. The sequence of ASCII or Unknown characters without
     * space will be put into a Token, One Hanzi character which has pinyin will be treated as a
     * Token. If there is no Chinese transliterator, the empty token array is returned.
     */
    public ArrayList<Token> getTokens(final String input) {
        ArrayList<Token> tokens = new ArrayList<Token>();
        if (!hasChineseTransliterator() || TextUtils.isEmpty(input)) {
            // return empty tokens.
            return tokens;
        }

        final int inputLength = input.length();
        final StringBuilder sb = new StringBuilder();
        int tokenType = Token.LATIN;
        Token token = new Token();

        // Go through the input, create a new token when
        // a. Token type changed
        // b. Get the Pinyin of current charater.
        // c. current character is space.
        for (int i = 0; i < inputLength; i++) {
            final char character = input.charAt(i);
            if (Character.isSpaceChar(character)) {
                if (sb.length() > 0) {
                    addToken(sb, tokens, tokenType);
                }
            } else {
                tokenize(character, token);
                if (token.type == Token.PINYIN) {
                    if (sb.length() > 0) {
                        addToken(sb, tokens, tokenType);
                    }
                    tokens.add(token);
                    token = new Token();
                } else {
                    if (tokenType != token.type && sb.length() > 0) {
                        addToken(sb, tokens, tokenType);
                    }
                    sb.append(token.target);
                }
                tokenType = token.type;
            }
        }
        if (sb.length() > 0) {
            addToken(sb, tokens, tokenType);
        }
        return tokens;
    }

    private void addToken(
            final StringBuilder sb, final ArrayList<Token> tokens, final int tokenType) {
        String str = sb.toString();
        tokens.add(new Token(tokenType, str, str));
        sb.setLength(0);
    }

// ----------------- MTK --------------------------------

    /// The following lines are provided and maintained by Mediatek Inc.New
    // Feature ALPS00338325.
    /// First upper, last upper and last lower Russian character
    private static final char FIRST_RUSSIAN_UPPER = '\u0410';
    private static final char LAST_RUSSIAN_UPPER = '\u042f';
    private static final char LAST_RUSSIAN_LOWER = '\u044f';
    /// First and last Arabic character
    private static final char FIRST_ARABIC = '\u0628';
    private static final char LAST_ARABIC = '\u0649';
    /// First and last Hebrew character
    private static final char FIRST_HEBREW = '\u05d0';
    private static final char LAST_HEBREW = '\u05ea';
    /// The previous lines are provided and maintained by Mediatek Inc.

    /// The following lines are provided and maintained by Mediatek Inc.
    // New Feature ALPS00338325.
    public static final int RUSSIAN = 14;
    public static final int ARABIC = 15;
    public static final int HEBREW = 16;
    /// The previous lines are provided and maintained by Mediatek Inc.

    /// The following lines are provided and maintained by Mediatek Inc.
    //New Feature ALPS00338325: dialer search support Russian, Arabic, Hebrew.
    private static Map<Character, Character> sMuiSupportMap = new HashMap<Character, Character>();
    // support Russian
    static {
        sMuiSupportMap.put('\u0410', '2'); // А
        sMuiSupportMap.put('\u0411', '2'); // Б
        sMuiSupportMap.put('\u0412', '2'); // В
        sMuiSupportMap.put('\u0413', '2'); // Г

        sMuiSupportMap.put('\u0414', '3'); // Д
        sMuiSupportMap.put('\u0415', '3'); // Е
        sMuiSupportMap.put('\u0416', '3'); // Ж
        sMuiSupportMap.put('\u0417', '3'); // З

        sMuiSupportMap.put('\u0418', '4'); // И
        sMuiSupportMap.put('\u0419', '4'); // Й
        sMuiSupportMap.put('\u041a', '4'); // К
        sMuiSupportMap.put('\u041b', '4'); // Л

        sMuiSupportMap.put('\u041c', '5'); // М
        sMuiSupportMap.put('\u041d', '5'); // Н
        sMuiSupportMap.put('\u041e', '5'); // О
        sMuiSupportMap.put('\u041f', '5'); // П

        sMuiSupportMap.put('\u0420', '6'); // Р
        sMuiSupportMap.put('\u0421', '6'); // С
        sMuiSupportMap.put('\u0422', '6'); // Т
        sMuiSupportMap.put('\u0423', '6'); // У

        sMuiSupportMap.put('\u0424', '7'); // Ф
        sMuiSupportMap.put('\u0425', '7'); // Х
        sMuiSupportMap.put('\u0426', '7'); // Ц
        sMuiSupportMap.put('\u0427', '7'); // Ч

        sMuiSupportMap.put('\u0428', '8'); // Ш
        sMuiSupportMap.put('\u0429', '8'); // Щ
        sMuiSupportMap.put('\u042a', '8'); // Ъ
        sMuiSupportMap.put('\u042b', '8'); // Ы

        sMuiSupportMap.put('\u042c', '9'); // Ь
        sMuiSupportMap.put('\u042d', '9'); // Э
        sMuiSupportMap.put('\u042e', '9'); // Ю
        sMuiSupportMap.put('\u042f', '9'); // Я

        sMuiSupportMap.put('\u0430', '2'); // а
        sMuiSupportMap.put('\u0431', '2'); // б
        sMuiSupportMap.put('\u0432', '2'); // в
        sMuiSupportMap.put('\u0433', '2'); // г

        sMuiSupportMap.put('\u0434', '3'); // д
        sMuiSupportMap.put('\u0435', '3'); // е
        sMuiSupportMap.put('\u0436', '3'); // ж
        sMuiSupportMap.put('\u0437', '3'); // з

        sMuiSupportMap.put('\u0438', '4'); // и
        sMuiSupportMap.put('\u0439', '4'); // й
        sMuiSupportMap.put('\u043a', '4'); // к
        sMuiSupportMap.put('\u043b', '4'); // л

        sMuiSupportMap.put('\u043c', '5'); // м
        sMuiSupportMap.put('\u043d', '5'); // н
        sMuiSupportMap.put('\u043e', '5'); // о
        sMuiSupportMap.put('\u043f', '5'); // п

        sMuiSupportMap.put('\u0440', '6'); // р
        sMuiSupportMap.put('\u0441', '6'); // с
        sMuiSupportMap.put('\u0442', '6'); // т
        sMuiSupportMap.put('\u0443', '6'); // у

        sMuiSupportMap.put('\u0444', '7'); // ф
        sMuiSupportMap.put('\u0445', '7'); // х
        sMuiSupportMap.put('\u0446', '7'); // ц
        sMuiSupportMap.put('\u0447', '7'); // ч

        sMuiSupportMap.put('\u0448', '8'); // ш
        sMuiSupportMap.put('\u0449', '8'); // щ
        sMuiSupportMap.put('\u044a', '8'); // ъ
        sMuiSupportMap.put('\u044b', '8'); // ы

        sMuiSupportMap.put('\u044c', '9'); // ь
        sMuiSupportMap.put('\u044d', '9'); // э
        sMuiSupportMap.put('\u044e', '9'); // ю
        sMuiSupportMap.put('\u044f', '9'); // я

        sMuiSupportMap.put('\u0401', '3'); // Ё
        sMuiSupportMap.put('\u0451', '3'); // ё
    }

    // support Arabic
    static {
        sMuiSupportMap.put('\u0628', '2'); // ب
        sMuiSupportMap.put('\u0629', '2'); // ة
        sMuiSupportMap.put('\u062a', '2'); // ت
        sMuiSupportMap.put('\u062b', '2'); // ث

        sMuiSupportMap.put('\u0621', '3'); // ء
        sMuiSupportMap.put('\u0627', '3'); // ا

        sMuiSupportMap.put('\u0633', '4'); // س
        sMuiSupportMap.put('\u0634', '4'); // ش
        sMuiSupportMap.put('\u0635', '4'); // ص
        sMuiSupportMap.put('\u0636', '4'); // ض

        sMuiSupportMap.put('\u062f', '5'); // د
        sMuiSupportMap.put('\u0630', '5'); // ذ
        sMuiSupportMap.put('\u0631', '5'); // ر
        sMuiSupportMap.put('\u0632', '5'); // ز

        sMuiSupportMap.put('\u062c', '6'); // ج
        sMuiSupportMap.put('\u062d', '6'); // ح
        sMuiSupportMap.put('\u062e', '6'); // خ

        sMuiSupportMap.put('\u0646', '7'); // ن
        sMuiSupportMap.put('\u0647', '7'); // ه
        sMuiSupportMap.put('\u0648', '7'); // و
        sMuiSupportMap.put('\u0649', '7'); // ى

        sMuiSupportMap.put('\u0641', '8'); // ف
        sMuiSupportMap.put('\u0642', '8'); // ق
        sMuiSupportMap.put('\u0643', '8'); // ك
        sMuiSupportMap.put('\u0644', '8'); // ل
        sMuiSupportMap.put('\u0645', '8'); // م

        sMuiSupportMap.put('\u0637', '9'); // ط
        sMuiSupportMap.put('\u0638', '9'); // ظ
        sMuiSupportMap.put('\u0639', '9'); // ع
        sMuiSupportMap.put('\u063a', '9'); // غ
    }
    // support Hebrew
    static {
        sMuiSupportMap.put('\u05d3', '2'); // ד
        sMuiSupportMap.put('\u05d4', '2'); // ה
        sMuiSupportMap.put('\u05d5', '2'); // ו

        sMuiSupportMap.put('\u05d0', '3'); // א
        sMuiSupportMap.put('\u05d1', '3'); // ב
        sMuiSupportMap.put('\u05d2', '3'); // ג

        sMuiSupportMap.put('\u05de', '4'); // מ
        sMuiSupportMap.put('\u05e0', '4'); // נ

        sMuiSupportMap.put('\u05dc', '5'); // ל
        sMuiSupportMap.put('\u05db', '5'); // כ

        sMuiSupportMap.put('\u05d6', '6'); // ז
        sMuiSupportMap.put('\u05d7', '6'); // ח
        sMuiSupportMap.put('\u05d8', '6'); // ט

        sMuiSupportMap.put('\u05e8', '7'); // ר
        sMuiSupportMap.put('\u05e9', '7'); // ש
        sMuiSupportMap.put('\u05ea', '7'); // ת

        sMuiSupportMap.put('\u05e6', '8'); // צ
        sMuiSupportMap.put('\u05e7', '8'); // ק

        sMuiSupportMap.put('\u05e1', '9'); // ס
        sMuiSupportMap.put('\u05e2', '9'); // ע
        sMuiSupportMap.put('\u05e3', '9'); // ף

    }
    /// The previous lines are provided and maintained by Mediatek Inc.

    /**
     * M: The following lines are provided and maintained by Mediatek inc.
     */
    private class DialerSearchToken extends Token {
        static final int FIRSTCASE = 0;
        static final int UPPERCASE = 1;
        static final int LOWERCASE = 2;
    }

    /**
     * M: get Tokens For DialerSearch.
     * @param input input words
     * @param offsets a StringBuilder
     * @return tokens
     */
    public String getTokensForDialerSearch(final String input, StringBuilder offsets) {

        if (offsets == null || input == null || TextUtils.isEmpty(input)) {
            // return empty tokens
            return null;
        }

        StringBuilder subStrSet = new StringBuilder();
        ArrayList<Token> tokens = new ArrayList<Token>();
        ArrayList<String> shortSubStrOffset = new ArrayList<String>();
        final int inputLength = input.length();
        final StringBuilder subString = new StringBuilder();
        final StringBuilder subStrOffset = new StringBuilder();
        int tokenType = Token.LATIN;
        int caseTypePre = DialerSearchToken.FIRSTCASE;
        int caseTypeCurr = DialerSearchToken.UPPERCASE;
        int mPos = 0;

        // Go through the input, create a new token when
        // a. Token type changed
        // b. Get the Pinyin of current charater.
        // c. current character is space.
        // d. Token case changed from lower case to upper case,
        // e. the first character is always a separated one
        // f character == '+' || character == '#' || character == '*' || character == ',' ||
        // character == ';'
        for (int i = 0; i < inputLength; i++) {
            final char character = input.charAt(i);
            if (character == '-' || character == ',' || character == '(' || character == ')'
                    || character == '.' || character == '/') {
                mPos++;
            } else if (character == ' ') {
                if (subString.length() > 0) {
                    addToken(subString, tokens, tokenType);
                    addOffsets(subStrOffset, shortSubStrOffset);
                }
                addSubString(tokens, shortSubStrOffset, subStrSet, offsets);
                mPos++;
                caseTypePre = DialerSearchToken.FIRSTCASE;
            } else if (character < 256) {
                if (tokenType != Token.LATIN && subString.length() > 0) {
                    addToken(subString, tokens, tokenType);
                    addOffsets(subStrOffset, shortSubStrOffset);
                }
                caseTypeCurr = (character >= 'A' && character <= 'Z') ? DialerSearchToken.UPPERCASE
                        : DialerSearchToken.LOWERCASE;
                if (caseTypePre == DialerSearchToken.LOWERCASE
                        && caseTypeCurr == DialerSearchToken.UPPERCASE) {
                    addToken(subString, tokens, tokenType);
                    addOffsets(subStrOffset, shortSubStrOffset);
                }
                caseTypePre = caseTypeCurr;
                tokenType = Token.LATIN;
                Character c = Character.toUpperCase(character);
                if (c != null) {
                    subString.append(c);
                    subStrOffset.append((char) mPos);
                }
                mPos++;
                /// The following lines are provided and maintained by
                // Mediatek Inc.New Feature ALPS00338325.
                // if character is Russian
                /** M: add special character for Russian */
            } else if (isSpecialRussianCharacter(character)
                    || (character >= FIRST_RUSSIAN_UPPER && character <= LAST_RUSSIAN_LOWER)) {
                // if the pre character is not Russian, sub string from current character.
                if (tokenType != Token.RUSSIAN && subString.length() > 0) {
                    addToken(subString, tokens, tokenType);
                    addOffsets(subStrOffset, shortSubStrOffset);
                }
                // current character is upper or lower.
                /** M: add special character for Russian */
                caseTypeCurr = (isUpperCaseSpecialRussianChar(character) ||
                        (character >= FIRST_RUSSIAN_UPPER && character <= LAST_RUSSIAN_UPPER)) ?
                        DialerSearchToken.UPPERCASE : DialerSearchToken.LOWERCASE;
                // if current character is upper and pre character is lower,
                // sub string like English.
                if (caseTypePre == DialerSearchToken.LOWERCASE
                        && caseTypeCurr == DialerSearchToken.UPPERCASE) {
                    addToken(subString, tokens, tokenType);
                    addOffsets(subStrOffset, shortSubStrOffset);
                }
                // set caseTypePre and tokenType for next "for" circle.
                caseTypePre = caseTypeCurr;
                tokenType = Token.RUSSIAN;
                // set the number of the character in subString, not the Russian character.
                Character c = sMuiSupportMap.get(character);
                if (c != null) {
                    subString.append(c);
                    subStrOffset.append((char) mPos);
                }
                mPos++;
                // if character is Arabic
            } else if (character >= FIRST_ARABIC && character <= LAST_ARABIC) {
                // if the pre character is not Arabic, sub string from current character.
                if (tokenType != Token.ARABIC && subString.length() > 0) {
                    addToken(subString, tokens, tokenType);
                    addOffsets(subStrOffset, shortSubStrOffset);
                }

                caseTypePre = caseTypeCurr;
                tokenType = Token.ARABIC;
                Character c = sMuiSupportMap.get(character);
                if (c != null) {
                    subString.append(c);
                    subStrOffset.append((char) mPos);
                }
                mPos++;
                // if character is Hebrew
            } else if (character >= FIRST_HEBREW && character <= LAST_HEBREW) {
                // if the pre character is not Hebrew, sub string from current character.
                if (tokenType != Token.HEBREW && subString.length() > 0) {
                    addToken(subString, tokens, tokenType);
                    addOffsets(subStrOffset, shortSubStrOffset);
                }

                caseTypePre = caseTypeCurr;
                tokenType = Token.HEBREW;
                Character c = sMuiSupportMap.get(character);
                if (c != null) {
                    subString.append(c);
                    subStrOffset.append((char) mPos);
                }
                mPos++;
                // The previous lines are provided and maintained by Mediatek
                // Inc.
                // TODO Check
                // } else if (character < FIRST_UNIHAN) {
                // mPos++;
            } else {
                Token t = new Token();
                tokenize(character, t);
                int tokenSize = t.target.length();
                //Current type is PINYIN
                if (t.type == Token.PINYIN) {
                    if (subString.length() > 0) {
                        addToken(subString, tokens, tokenType);
                        addOffsets(subStrOffset, shortSubStrOffset);
                    }
                    tokens.add(t);
                    for (int j = 0; j < tokenSize; j++) {
                        subStrOffset.append((char) mPos);
                    }
                    addOffsets(subStrOffset, shortSubStrOffset);
                    tokenType = Token.PINYIN;
                    caseTypePre = DialerSearchToken.FIRSTCASE;
                    mPos++;
                } else {
                    mPos++;
                }
            }
            // If the name string is too long, cut it off to meet the storage request of dialer
            // search.
            if (mPos > 127) {
                break;
            }
        }
        if (subString.length() > 0) {
            addToken(subString, tokens, tokenType);
            addOffsets(subStrOffset, shortSubStrOffset);
        }
        addSubString(tokens, shortSubStrOffset, subStrSet, offsets);
        return subStrSet.toString();
    }

    public ArrayList<Token> getTokensForSpecialHanzi(final String input) {
        ArrayList<Token> tokens = new ArrayList<Token>();
        if (!hasChineseTransliterator() || TextUtils.isEmpty(input)) {
            return tokens;
        }
        final int inputLength = input.length();
        final StringBuilder sb = new StringBuilder();
        int tokenType = Token.LATIN;
        Token token = new Token();
        for (int i = 0; i < inputLength; i++) {
            final char character = input.charAt(i);
            if (Character.isSpaceChar(character)) {
                if (sb.length() > 0) {
                    addToken(sb, tokens, tokenType);
                }
            } else {
                tokenizeForSpecialHanzi(character, token);
                if (token.type == Token.PINYIN) {
                    if (sb.length() > 0) {
                        addToken(sb, tokens, tokenType);
                    }
                    tokens.add(token);
                    token = new Token();
                }
                tokenType = token.type;
            }
        }
        return tokens;
    }
    /**
     * M:whether the character is special Russian character?
     *
     * @param specialchar
     * @return true: special character, false: not special.
     */
    private boolean isSpecialRussianCharacter(final char specialchar) {
        return (SPECIAL_CHARS_LIST_UPPER.contains(specialchar) || SPECIAL_CHARS_LIST_LOWER
                .contains(specialchar));
    }

    /**
     * M:Judge the special Russian character is Upper case?
     *
     * @param specialchar
     * @return true: Upper case, false: Lower case
     */
    private boolean isUpperCaseSpecialRussianChar(final char specialchar) {
        return SPECIAL_CHARS_LIST_UPPER.contains(specialchar);
    }

    private void addOffsets(final StringBuilder sb, final ArrayList<String> shortSubStrOffset) {
        String str = sb.toString();
        shortSubStrOffset.add(str);
        sb.setLength(0);
    }

    private void addSubString(final ArrayList<Token> tokens,
                              final ArrayList<String> shortSubStrOffset,
                              StringBuilder subStrSet, StringBuilder offsets) {
        if (tokens == null || tokens.isEmpty()) {
            return;
        }

        int size = tokens.size();
        int len = 0;
        StringBuilder mShortSubStr = new StringBuilder();
        StringBuilder mShortSubStrOffsets = new StringBuilder();
        StringBuilder mShortSubStrSet = new StringBuilder();
        StringBuilder mShortSubStrOffsetsSet = new StringBuilder();

        for (int i = size - 1; i >= 0; i--) {
            String mTempStr = tokens.get(i).target;
            // add for CT NEW FEATURE
            // len += mTempStr.length();
            len = mTempStr.length();
            String mTempOffset = shortSubStrOffset.get(i);
            if (mShortSubStr.length() > 0) {
                // mShortSubStr.deleteCharAt(0);
                // mShortSubStrOffsets.deleteCharAt(0);
                // add for CT NEW FEATURE
                mShortSubStr.setLength(0);
                mShortSubStrOffsets.setLength(0);
            }
            mShortSubStr.insert(0, mTempStr);
            mShortSubStr.insert(0, (char) len);
            mShortSubStrOffsets.insert(0, mTempOffset);
            mShortSubStrOffsets.insert(0, (char) len);
            mShortSubStrSet.insert(0, mShortSubStr);
            mShortSubStrOffsetsSet.insert(0, mShortSubStrOffsets);
        }

        subStrSet.append(mShortSubStrSet);
        offsets.append(mShortSubStrOffsetsSet);
        tokens.clear();
        shortSubStrOffset.clear();
    }

    /**
     * M:Special character list for Russian.
     * */
    private static final ArrayList<Character> SPECIAL_CHARS_LIST_UPPER = new ArrayList<Character>();
    private static final ArrayList<Character> SPECIAL_CHARS_LIST_LOWER = new ArrayList<Character>();
    static {
        //Upper list
        SPECIAL_CHARS_LIST_UPPER.add('\u0401'); // Ё
        //Lower list
        SPECIAL_CHARS_LIST_LOWER.add('\u0451'); // ё
    }
    /**@}*/
    //The previous lines are provided and maintained by Mediatek inc.
}