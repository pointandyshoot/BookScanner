package au.id.pointandyshoot.bookscanner.core;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class MatcherTest {
    private final Matcher matcher=new Matcher();
    private WantedBook book(String title,String author){return new WantedBook("1",title,author,List.of(),true);}
    @Test public void findsAuthorWildcard(){assertEquals(1,matcher.find("JACKIE FRENCH",List.of(book("*","Jackie French"))).size());}
    @Test public void surnameAloneIsOnlyTentative(){assertFalse(matcher.find("FRENCH",List.of(book("*","Jackie French"))).get(0).strong);}
    @Test public void titleToleratesOneOcrError(){assertEquals(1,matcher.find("A GENTLEMAN IN MOSC0W",List.of(book("A Gentleman in Moscow",""))).size());}
    @Test public void shortTitlesNeedWholeExactWord(){assertTrue(matcher.find("NEON",List.of(book("Eon",""))).isEmpty());assertEquals(1,matcher.find("EON GREG BEAR",List.of(book("Eon",""))).size());}
    @Test public void punctuationAndAccentsNormalise(){assertEquals("gabriel garcia marquez",Matcher.normalise("GABRIEL GARCÍA MÁRQUEZ"));}
    @Test public void aliasesMatch(){assertEquals(1,matcher.find("THE PHILOSOPHERS STONE",List.of(new WantedBook("1","The Sorcerer’s Stone","",List.of("The Philosophers Stone"),true))).size());}
    @Test public void seriesAndSingleCharacterPatterns(){assertTrue(Matcher.glob("Matilda*","matilda s last waltz"));assertTrue(Matcher.glob("E?n","eon"));assertFalse(Matcher.glob("E?n","neon"));}
    @Test public void regexCharactersAreNotExecuted(){assertFalse(Matcher.glob("(a+)+*","bbbbbbbbbbbbbbbbbbbb"));}
    @Test public void disabledEntriesDoNotMatch(){assertTrue(matcher.find("Eon",List.of(new WantedBook("1","Eon","",List.of(),false))).isEmpty());}
    @Test public void authorOnlyIsExplicitlyWeak(){Matcher.Match m=matcher.find("GREG BEAR",List.of(book("Eon","Greg Bear"))).get(0);assertEquals("Author only — check title",m.reason);assertTrue(m.score<.8);}
    @Test(expected=IllegalArgumentException.class) public void rejectsEmptyWildcard(){book("*","");}
    @Test(expected=IllegalArgumentException.class) public void rejectsPunctuationOnlyEntry(){book("!!!","");}
    @Test public void partialTitleIsOrange(){assertFalse(matcher.find("MOSCOW",List.of(book("A Gentleman in Moscow",""))).get(0).strong);}
    @Test public void commonWordsDoNotCreateCandidates(){assertTrue(matcher.find("THE BOOK NOVEL VOLUME",List.of(book("The Book of Tomorrow",""))).isEmpty());}
    @Test public void authorOnlyNeverStrongForSpecificTitle(){assertFalse(matcher.find("GREG BEAR",List.of(book("Eon","Greg Bear"))).get(0).strong);}
    @Test public void fullTitleIsStrongEvidence(){assertTrue(matcher.find("HOUSE OF SUNS",List.of(book("House of Suns",""))).get(0).strong);}
    @Test public void fullAuthorWildcardIsStrong(){assertTrue(matcher.find("TERRY PRATCHETT",List.of(book("*","Terry Pratchett"))).get(0).strong);}
    @Test public void partialOcrErrorIsTentative(){assertFalse(matcher.find("PRATCHETI",List.of(book("*","Terry Pratchett"))).get(0).strong);}
    @Test public void unrelatedTitleDoesNotMatch(){assertTrue(matcher.find("THE SILENT PATIENT",List.of(book("All the Light We Cannot See",""))).isEmpty());}
}
