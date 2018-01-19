package nl.nn.adapterframework.webcontrol.api;

import java.net.URL;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public abstract class ChromeTestBase {
	public static WebDriver driver;
	private static final String USERNAME = "Baswat";
	private static final String AUTOMATE_KEY = "d835004c-97a1-4e52-b63f-daa5b1d3d3fd";
	private static String URL = "https://"+ USERNAME + ":" + AUTOMATE_KEY +  "@ondemand.saucelabs.com:443/wd/hub";

	@Before
	public void initDriver() throws Exception {
		// Set default settings!
		DesiredCapabilities caps = DesiredCapabilities.chrome();
		caps.setCapability("platform", "Windows");
		caps.setCapability("version", "43.0");
		
		if( System.getProperty("TRAVIS_JOB_NUMBER") != null ){
			caps.setCapability("tunnel-identifier", System.getProperty("TRAVIS_JOB_NUMBER"));
		}
		
		driver = new RemoteWebDriver(new URL(URL), caps);
		driver.get("http://localhost:8080/iaf/gui/#/status");
		
		System.out.println("Source of webpage : " + driver.getPageSource() );
	}
	
	@After
	public void quit() throws Exception {
		driver.quit();
	}
	
	public void waitUntilVisible(By by) throws Exception {
		waitUntilVisible(by, 10);
	}
		
	public void waitUntilVisible(By by, int timeout) throws Exception {
		new WebDriverWait(driver, timeout).until( ExpectedConditions.visibilityOfElementLocated(by) );
	}
}
