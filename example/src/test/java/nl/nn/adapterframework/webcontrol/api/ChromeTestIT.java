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
		navigations.put("Configurations", "#side-menu li:nth-child(3) a");
		navigations.put("Logging", "#side-menu li:nth-child(4) a");
	}
	
	@Test
	public void PageTitle() throws Exception {
		Assert.assertEquals("IAF | Adapter Status", driver.getTitle());
	}
	
	@Test
	public void Navigation() throws Exception {
		Iterator<Entry<String, String>> it = navigations.entrySet().iterator();
		
		while(it.hasNext()){
			Map.Entry pair = (Map.Entry)it.next();
			waitAndClick( By.cssSelector( (String) pair.getValue() ) );
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