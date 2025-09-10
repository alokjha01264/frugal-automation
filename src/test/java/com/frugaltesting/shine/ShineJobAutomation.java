package com.frugaltesting.shine;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.chrome.ChromeDriver;

public class ShineJobAutomation {

    private static final String EMAIL = System.getenv().getOrDefault("SHINE_EMAIL", "example@gmail.com");
    private static final String PASSWORD = System.getenv().getOrDefault("SHINE_PASSWORD", "yourpassword");
    private static final long PAUSE_AFTER_APPLY_MS = Long.parseLong(System.getenv().getOrDefault("PAUSE_AFTER_APPLY_MS", "40000"));

    private WebDriver driver;
    private WebDriverWait wait;
    private final Path screenshotsDir = Path.of("screenshots");

    public ShineJobAutomation() {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--start-maximized");
        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        try {
            if (!Files.exists(screenshotsDir)) {
                Files.createDirectories(screenshotsDir);
            }
        } catch (IOException e) {
            System.err.println("Could not create screenshots dir: " + e.getMessage());
        }
    }

    public void run() {
        try {
            openAndLogin();
            takeScreenshot("after_login");
            searchJob("Software Tester", "Hyderabad", "2");
            selectSecondJobAndApply();
        } catch (Exception e) {
            System.err.println("Exception during automation: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
    }

    private void openAndLogin() {
        driver.get("https://www.shine.com");
        System.out.println("Page title: " + driver.getTitle());
        System.out.println("Current URL: " + driver.getCurrentUrl());
        try {
            List<WebElement> loginAnchors = driver.findElements(
                    By.xpath("//a[contains(@href,'/myshine/login') or contains(text(),'Login') or contains(@href,'/myshine/login/')]"));
            if (!loginAnchors.isEmpty()) {
                loginAnchors.get(0).click();
            } else {
                WebElement loginBtn = driver.findElement(By.cssSelector("button.cls_base_1_pw_login_btn, a[href*='/myshine/login']"));
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", loginBtn);
            }
        } catch (Exception e) {
            System.out.println("Could not click login anchor/button via common locators, attempting JS fallback.");
            ((JavascriptExecutor) driver)
                    .executeScript("document.querySelector('a[href*=\"/myshine/login/\"]') && document.querySelector('a[href*=\"/myshine/login/\"]').click();");
        }
        wait.until(ExpectedConditions.or(
                ExpectedConditions.urlContains("/myshine/login"),
                ExpectedConditions.visibilityOfElementLocated(By.id("id_email")),
                ExpectedConditions.visibilityOfElementLocated(By.id("id_email_login")),
                ExpectedConditions.visibilityOfElementLocated(By.id("id_password")),
                ExpectedConditions.visibilityOfElementLocated(By.id("Password"))
        ));
        By emailLocatorDesktop = By.id("id_email");
        By passwordLocatorDesktop = By.id("Password");
        By emailLocatorModal = By.id("id_email_login");
        By passwordLocatorModal = By.id("id_password");
        WebElement emailEl = null;
        WebElement passEl = null;
        try {
            emailEl = wait.until(ExpectedConditions.visibilityOfElementLocated(emailLocatorDesktop));
            passEl = wait.until(ExpectedConditions.visibilityOfElementLocated(passwordLocatorDesktop));
        } catch (Exception ignored) {
            try {
                emailEl = wait.until(ExpectedConditions.visibilityOfElementLocated(emailLocatorModal));
                passEl = wait.until(ExpectedConditions.visibilityOfElementLocated(passwordLocatorModal));
            } catch (Exception ex) {
                throw new RuntimeException("Login fields not found on page.");
            }
        }
        emailEl.clear();
        emailEl.sendKeys(EMAIL);
        passEl.clear();
        passEl.sendKeys(PASSWORD);
        try {
            WebElement submitBtn = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("button.cls_base_1_pw_login_btn")));
            submitBtn.click();
        } catch (Exception e) {
            passEl.submit();
        }
        try {
            wait.until(driver -> {
                if (driver.getCurrentUrl().contains("/myshine/") || driver.getCurrentUrl().contains("/dashboard")) {
                    return true;
                }
                boolean loginStillPresent = !driver.findElements(By.cssSelector("a[href*='/myshine/login'], button.cls_base_1_pw_login_btn")).isEmpty();
                if (!loginStillPresent) return true;
                return !driver.findElements(By.cssSelector("a[href*='/profile'], .profile, .avatar")).isEmpty();
            });
        } catch (Exception e) {
            System.out.println("Login success detection timed out; continuing - check if logged in manually.");
        }
        System.out.println("Login step done. Current URL: " + driver.getCurrentUrl());
    }

    private void searchJob(String title, String location, String experience) {
        String[] searchUrls = new String[]{
                "https://www.shine.com/job-search/",
                "https://www.shine.com/new/job-search/",
                "https://www.shine.com/job-search/jobs-in-all-locations/"
        };
        boolean pageLoaded = false;
        for (String u : searchUrls) {
            try {
                driver.get(u);
                WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(6));
                shortWait.until(ExpectedConditions.or(
                        ExpectedConditions.visibilityOfElementLocated(By.id("id_q")),
                        ExpectedConditions.visibilityOfElementLocated(By.id("id_mainBody_search")),
                        ExpectedConditions.visibilityOfElementLocated(By.id("submit_search"))
                ));
                pageLoaded = true;
                System.out.println("Navigated to search page: " + u);
                break;
            } catch (Exception e) {
            }
        }
        if (!pageLoaded) {
            try {
                WebElement searchIcon = driver.findElement(By.cssSelector("div#id_searchJob, .search-icon"));
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", searchIcon);
                wait.until(ExpectedConditions.or(
                        ExpectedConditions.visibilityOfElementLocated(By.id("id_q")),
                        ExpectedConditions.visibilityOfElementLocated(By.id("id_mainBody_search")),
                        ExpectedConditions.visibilityOfElementLocated(By.id("submit_search"))
                ));
                pageLoaded = true;
                System.out.println("Opened search via header search icon.");
            } catch (Exception ignored) {
            }
        }
        if (!pageLoaded) {
            throw new RuntimeException("Could not load job search page.");
        }
        WebElement titleField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("id_q")));
        WebElement locField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("id_loc")));
        titleField.clear();
        titleField.sendKeys(title);
        locField.clear();
        locField.sendKeys(location);
        takeScreenshot("after_search_inputs");
        try {
            WebElement expSelectEl = driver.findElement(By.id("id_minexp"));
            if (expSelectEl.isDisplayed()) {
                Select sel = new Select(expSelectEl);
                try {
                    sel.selectByVisibleText(experience);
                } catch (Exception ex) {
                    sel.selectByVisibleText(experience + " Yrs");
                }
            }
        } catch (Exception ignored) {
        }
        try {
            WebElement searchBtn = wait.until(ExpectedConditions.elementToBeClickable(By.id("submit_search")));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", searchBtn);
        } catch (Exception e1) {
            try {
                WebElement searchBtn2 = wait.until(ExpectedConditions.elementToBeClickable(
                        By.xpath("//input[@type='submit' and contains(@value,'Search')]|//button[contains(text(),'Search')]")));
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", searchBtn2);
            } catch (Exception e2) {
                titleField.submit();
            }
        }
        By jobListItems = By.xpath("//ul[contains(@class,'job-list')]/li | //div[contains(@class,'jobCard') or contains(@class,'job-listing')] | //a[contains(@href,'/job/')]");
        new WebDriverWait(driver, Duration.ofSeconds(30)).until(ExpectedConditions.numberOfElementsToBeMoreThan(jobListItems, 1));
        System.out.println("Search results loaded. URL: " + driver.getCurrentUrl());
    }

    private void selectSecondJobAndApply() throws IOException {
        List<WebElement> bigCards = driver.findElements(By.cssSelector("div.jobCardNova_bigCard__W2xn3[itemprop='itemListElement']"));
        List<WebElement> visibleBigCards = bigCards.stream().filter(WebElement::isDisplayed).collect(Collectors.toList());
        WebElement secondCard = null;
        for (WebElement card : visibleBigCards) {
            try {
                WebElement posMeta = card.findElement(By.cssSelector("meta[itemprop='position']"));
                String pos = posMeta.getAttribute("content");
                if ("2".equals(pos) || "2".equals(card.getAttribute("data-position"))) {
                    secondCard = card;
                    break;
                }
            } catch (Exception ignored) {
            }
        }
        if (secondCard == null && visibleBigCards.size() >= 2) {
            secondCard = visibleBigCards.get(1);
        }
        if (secondCard == null) {
            List<WebElement> anchors = driver.findElements(By.xpath(
                    "//div[contains(@class,'jobCard') or contains(@class,'jobCardNova') or contains(@class,'job-listing')]" +
                            "//a[contains(@href,'/job/') and normalize-space()!='']"
            ));
            List<WebElement> visibleAnchors = anchors.stream()
                    .filter(WebElement::isDisplayed)
                    .filter(a -> {
                        try {
                            return a.getAttribute("href") != null && !a.getAttribute("href").trim().isEmpty();
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .collect(Collectors.toList());
            if (visibleAnchors.size() < 2) {
                throw new RuntimeException("Less than 2 visible job listings found. Found: " + visibleAnchors.size());
            }
            WebElement secondAnchor = visibleAnchors.get(1);
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", secondAnchor);
            try {
                secondAnchor.click();
            } catch (Exception e) {
                String href = secondAnchor.getAttribute("href");
                if (href != null && !href.trim().isEmpty()) {
                    driver.get(href);
                } else {
                    throw new RuntimeException("Unable to open second job anchor.");
                }
            }
        } else {
            try {
                WebElement titleAnchor = secondCard.findElement(By.cssSelector("p[itemprop='name'] a, .jobCardNova_bigCardTopTitle__vLLav a, a[href*='/jobs/'], a[href*='/job/']"));
                String href = titleAnchor.getAttribute("href");
                String title = titleAnchor.getText().trim();
                String company = "(unknown)";
                try {
                    WebElement comp = secondCard.findElement(By.cssSelector(".jobCardNova_bigCardTopTitleName__M_W_m, .jobCardNova_bigCardTopTitleName__M_W_m.jdTruncationCompany, [title][class*='company']"));
                    company = comp.getText().trim();
                } catch (Exception ignored) {
                }
                String experience = "";
                try {
                    WebElement expEl = secondCard.findElement(By.cssSelector(".jobCardNova_bigCardCenterListExp__KTSEc"));
                    experience = expEl.getText().trim();
                } catch (Exception ignored) {
                }
                String salary = "";
                try {
                    List<WebElement> centerList = secondCard.findElements(By.cssSelector(".jobCardNova_bigCardCenter__uVExC .jobCardNova_bigCardCenterList__GcAWv .jobCardNova_bigCardCenterListExp__KTSEc"));
                    if (centerList.size() >= 2) salary = centerList.get(1).getText().trim();
                } catch (Exception ignored) {
                }
                String location = "";
                try {
                    WebElement loc = secondCard.findElement(By.cssSelector(".jobCardNova_limitsLocation__eHDH7, .jobCardNova_bigCardLocation__OMkI1"));
                    location = loc.getText().trim();
                } catch (Exception ignored) {
                }
                System.out.println("Selected 2nd job -> Title: " + title + " | Company: " + company + " | Location: " + location + " | Exp: " + experience + " | Salary: " + salary + " | href: " + href);
                ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", titleAnchor);
                try {
                    titleAnchor.click();
                } catch (Exception clickEx) {
                    if (href != null && !href.trim().isEmpty()) driver.get(href);
                    else throw new RuntimeException("Unable to click/open job detail from second card.");
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to extract/open second job card: " + e.getMessage(), e);
            }
        }
        String original = driver.getWindowHandle();
        Set<String> handles = driver.getWindowHandles();
        for (String handle : handles) {
            if (!handle.equals(original)) {
                driver.switchTo().window(handle);
                break;
            }
        }
        wait.until(ExpectedConditions.or(
                ExpectedConditions.visibilityOfElementLocated(By.xpath("//h1 | //h2 | //h3[contains(@class,'job-title')]")),
                ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".jobDetail, .job-description, .job-desc"))
        ));
        takeScreenshot("after_select_second_job");
        By applyBtnSelectors = By.xpath("//button[contains(text(),'Apply') or contains(text(),'APPLY') or contains(text(),'Apply Now') or contains(@class,'apply')]");
        wait.until(ExpectedConditions.elementToBeClickable(applyBtnSelectors));
        WebElement applyBtn = driver.findElement(applyBtnSelectors);
        try {
            String detailJobTitle = driver.findElement(By.xpath("//h1 | //h2 | //h3[contains(@class,'job-title')]")).getText();
            System.out.println("Job detail page title: " + detailJobTitle);
        } catch (Exception ignored) {
        }
        System.out.println("Clicking Apply button now...");
        applyBtn.click();
        takeScreenshot("after_click_apply");
        try {
            By getCallBtn = By.xpath("//button[contains(., 'Get Call') and contains(@class,'interviewAssuredModal')]");
            By getCallBtnFallback = By.xpath("//button[.//img[@alt='callIcon'] or contains(., 'Get Call in 15') or contains(@class,'interviewAssuredModalNova_interviewModalInnerBottomBtn')]");
            WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(8));
            WebElement callBtn = null;
            try {
                callBtn = shortWait.until(ExpectedConditions.elementToBeClickable(getCallBtn));
            } catch (Exception e) {
                try {
                    callBtn = shortWait.until(ExpectedConditions.elementToBeClickable(getCallBtnFallback));
                } catch (Exception ignored) {
                }
            }
            if (callBtn != null) {
                System.out.println("Found 'Get Call' popup button - clicking it...");
                try {
                    callBtn.click();
                } catch (Exception clickEx) {
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", callBtn);
                }
                takeScreenshot("after_click_get_call");
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                try {
                    By doneBtn = By.xpath("//button[contains(text(),'Done') and (contains(@class,'youAreAllSetModalNova_youAreModalInnerBottomBtn') or contains(@class,'nova_btn'))]");
                    WebElement done = shortWait.until(ExpectedConditions.elementToBeClickable(doneBtn));
                    System.out.println("Clicking 'Done' button...");
                    try {
                        done.click();
                    } catch (Exception clickEx) {
                        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", done);
                    }
                    takeScreenshot("after_click_done");
                } catch (Exception e) {
                    System.out.println("'Done' button not found after Get Call popup.");
                }
            } else {
                System.out.println("'Get Call' popup did not appear (or wasn't clickable).");
            }
        } catch (Exception e) {
            System.err.println("Exception while handling 'Get Call' popup: " + e.getMessage());
        }
        System.out.println("Pausing script for " + PAUSE_AFTER_APPLY_MS + " ms so you can inspect the browser. (Thread.sleep)");
        try {
            Thread.sleep(PAUSE_AFTER_APPLY_MS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            System.out.println("Sleep interrupted.");
        }
        System.out.println("Resuming script after pause - attempting verification.");
        boolean applied = false;
        try {
            By appliedText = By.xpath("//*[contains(text(),'Applied') or contains(text(),'Application submitted') or contains(text(),'You have applied')]");
            wait.withTimeout(Duration.ofSeconds(8)).until(ExpectedConditions.visibilityOfElementLocated(appliedText));
            System.out.println("Confirmation message: " + driver.findElement(appliedText).getText());
            applied = true;
        } catch (Exception ignored) {
        }
        if (!applied) {
            try {
                WebElement newBtn = driver.findElement(applyBtnSelectors);
                String txt = newBtn.getText();
                if (txt != null && txt.toLowerCase().contains("applied")) {
                    System.out.println("Apply button text indicates applied: '" + txt + "'");
                    applied = true;
                }
            } catch (Exception ignored) {
            }
        }
        if (!applied) {
            try {
                By tick = By.xpath("//*[contains(@class,'tick') or contains(@class,'applied') or .//*[name()='svg' and (contains(@class,'tick') or contains(@class,'check'))]]");
                if (!driver.findElements(tick).isEmpty()) {
                    System.out.println("Found tick / applied icon on page.");
                    applied = true;
                }
            } catch (Exception ignored) {
            }
        }
        if (applied) {
            takeScreenshot("after_applying_success");
            System.out.println("Application verified as submitted.");
        } else {
            takeScreenshot("after_applying_failed");
            System.err.println("Could not verify application submission.");
        }
        try {
            if (!driver.getWindowHandle().equals(original)) {
                driver.close();
                driver.switchTo().window(original);
            }
        } catch (Exception ignored) {
        }
    }

    private void takeScreenshot(String name) {
        try {
            if (!(driver instanceof TakesScreenshot)) return;
            File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            Path dest = screenshotsDir.resolve(name + "_" + timestamp + ".png");
            Files.copy(src.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Saved screenshot: " + dest.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Screenshot failed: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        ShineJobAutomation bot = new ShineJobAutomation();
        bot.run();
    }
}
