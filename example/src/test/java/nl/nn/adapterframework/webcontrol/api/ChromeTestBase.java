package nl.nn.adapterframework.webcontrol.api;

import java.net.URL;

import org.junit.After;
import org.junit.AfterClass;
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
	public static Integer defaultTimeout = 30;
	private static String tunnelId = System.getenv("TRAVIS_JOB_NUMBER"); 
	private static final String USERNAME = "Baswat";
	private static final String AUTOMATE_KEY = "d835004c-97a1-4e52-b63f-daa5b1d3d3fd";
	private static String URL = "https://"+ USERNAME + ":" + AUTOMATE_KEY +  "@ondemand.saucelabs.com:443/wd/hub";

	@BeforeClass
	public void initDriver() throws Exception {
		// Set default settings!
		DesiredCapabilities caps = DesiredCapabilities.chrome();
		caps.setCapability("platform", "Windows");
		caps.setCapability("version", "43.0");
		
		if( tunnelId != null ){
			caps.setCapability("tunnel-identifier", tunnelId);
			System.out.println("Travis_JOB_NUMBER = " + tunnelId);
		}
		
		driver = new RemoteWebDriver(new URL(URL), caps);
		driver.get("http://127.0.0.1:8080/iaf/gui/#/status");
	}
	
	@AfterClass
	public void quit() throws Exception {
		driver.quit();
	}
	
	public void waitUntilVisible(By by) throws Exception {
		waitUntilVisible(by, defaultTimeout);
	}
		
	public void waitUntilVisible(By by, int timeout) throws Exception {
		new WebDriverWait(driver, timeout).until( ExpectedConditions.visibilityOfElementLocated(by) );
	}
	
	public void waitAndClick(By by) throws Exception {
		waitAndClick(by, defaultTimeout);
	}
	
	public void waitAndClick(By by, int timeout) throws Exception {
		waitUntilVisible(by, timeout);
		driver.findElement(by).click();
	}
}
