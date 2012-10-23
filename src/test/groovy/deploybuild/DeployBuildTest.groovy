package deploybuild

import org.jvnet.hudson.test.HudsonTestCase

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
public class DeployBuildTest extends HudsonTestCase {
    /**
     * Makes sure that the configuration survives the round trip.
     */
    public void testConfigRoundtrip() {
        def p = createFreeStyleProject();
        def l = [new DeployTarget("a", "b", "c", true), new DeployTarget("", "", "", false)]

        p.publishersList.add(new DeployBuild(l));
        submit(createWebClient().getPage(p,"configure").getFormByName("config"));

        def r = p.publishersList.get(DeployBuild.class)
        assertEquals(2,r.deployTargets.size())

        (0..1).each {
            assertEqualBeans(l[it],r.deployTargets[it],"deployName,deployDir,deployFiles,successOnly");
        }
    }

}
