package nl.nn.adapterframework.webcontrol.api;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.junit.Test;
import org.junit.Assert;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class ChromeTestIT extends ChromeTestBase {
	public static Map<String, String> navigations = new HashMap<String, String>();

	public ChromeTestIT() throws Exception {
		navigations.put("Configurations", "ul#side-menu li:nth-child(3) > a");
		navigations.put("Logging", "ul#side-menu li:nth-child(5) > a");
		navigations.put("Test a PipeLine", "ul#side-menu li:nth-child(7) > a");
		navigations.put("Test a ServiceListener","ul#side-menu li:nth-child(8) > a");
		navigations.put("Webservices", "ul#side-menu li:nth-child(9) > a");
		navigations.put("Scheduler", "ul#side-menu li:nth-child(10) > a");
		navigations.put("Environment Variables", "ul#side-menu li:nth-child(11) > a");
		navigations.put("Security Items", "ul#side-menu li:nth-child(13) > a");
		
		// TODO : JMS, JDBC, TESTING
	}
	
	@Test
	public void PageTitle() throws Exception {
		Assert.assertEquals("IAF | Adapter Status", driver.getTitle());
	}
	
	@Test
	public void Navigation() throws Exception {
		Iterator<Entry<String, String>> it = navigations.entrySet().iterator();
		
		while( it.hasNext() ){
			Map.Entry pair = (Map.Entry)it.next();
			waitAndClick( By.cssSelector( (String) pair.getValue() ) );
			System.out.println("Current page: " + driver.getTitle());
			Assert.assertEquals("IAF | " + pair.getKey(), driver.getTitle() );
			it.remove();
		}
//		waitAndClick(By.cssSelector(navConfigurationSelector));
//		Assert.assertEquals("IAF | Configurations", driver.getTitle() );
//		
//		waitAndClick(By.cssSelector(navLoggingSelector));
//		Assert.assertEquals("IAF | Logging", driver.getTitle() );
	}
}	