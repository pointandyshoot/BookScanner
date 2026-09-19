package au.id.pointandyshoot.bookscanner.core;
import org.junit.Test;
import java.util.Set;
import static org.junit.Assert.*;
public class AppearanceGateTest {
    @Test public void buzzesOnceWhileContinuouslyVisible(){AppearanceGate g=new AppearanceGate();assertTrue(g.update(Set.of("book"),0));for(int t=100;t<10000;t+=100)assertFalse(g.update(Set.of("book"),t));}
    @Test public void briefMissDoesNotRearm(){AppearanceGate g=new AppearanceGate();g.update(Set.of("book"),0);g.update(Set.of(),200);assertFalse(g.update(Set.of("book"),800));}
    @Test public void leavingAndReturningRearms(){AppearanceGate g=new AppearanceGate();g.update(Set.of("book"),0);g.update(Set.of(),1000);assertTrue(g.update(Set.of("book"),1600));}
    @Test public void differentBooksAlertIndependently(){AppearanceGate g=new AppearanceGate();g.update(Set.of("one"),0);assertTrue(g.update(Set.of("one","two"),100));assertFalse(g.update(Set.of("one","two"),200));}
    @Test public void overlappingAliasesShareEntryIdentity(){AppearanceGate g=new AppearanceGate();assertTrue(g.update(Set.of("same-id"),0));assertFalse(g.update(Set.of("same-id"),100));}
}
