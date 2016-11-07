package com.angkorteam.mbaas.server;

import com.angkorteam.mbaas.model.entity.Tables;
import com.angkorteam.mbaas.model.entity.tables.GroovyTable;
import com.angkorteam.mbaas.model.entity.tables.LayoutTable;
import com.angkorteam.mbaas.model.entity.tables.PageTable;
import com.angkorteam.mbaas.model.entity.tables.RestTable;
import com.angkorteam.mbaas.model.entity.tables.pojos.GroovyPojo;
import com.angkorteam.mbaas.model.entity.tables.pojos.LayoutPojo;
import com.angkorteam.mbaas.model.entity.tables.pojos.PagePojo;
import com.angkorteam.mbaas.model.entity.tables.pojos.RestPojo;
import com.angkorteam.mbaas.server.bean.AuthorizationStrategy;
import com.angkorteam.mbaas.server.bean.ClassResolver;
import com.angkorteam.mbaas.server.bean.GroovyClassLoader;
import com.angkorteam.mbaas.server.page.DashboardPage;
import com.angkorteam.mbaas.server.page.LoginPage;
import groovy.lang.GroovyCodeSource;
import org.apache.wicket.Page;
import org.apache.wicket.WicketRuntimeException;
import org.apache.wicket.authroles.authentication.AbstractAuthenticatedWebSession;
import org.apache.wicket.authroles.authentication.AuthenticatedWebApplication;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.resource.DynamicJQueryResourceReference;
import org.apache.wicket.settings.ExceptionSettings;
import org.jooq.DSLContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by socheat on 10/23/16.
 */
public class Application extends AuthenticatedWebApplication {

    public static final List<Character> CHARACTERS = new ArrayList<>();
    public static final List<Character> NUMBERS = new ArrayList<>();

    static {
        CHARACTERS.add('a');
        CHARACTERS.add('b');
        CHARACTERS.add('c');
        CHARACTERS.add('d');
        CHARACTERS.add('e');
        CHARACTERS.add('f');
        CHARACTERS.add('g');
        CHARACTERS.add('h');
        CHARACTERS.add('i');
        CHARACTERS.add('j');
        CHARACTERS.add('k');
        CHARACTERS.add('l');
        CHARACTERS.add('m');
        CHARACTERS.add('n');
        CHARACTERS.add('o');
        CHARACTERS.add('p');
        CHARACTERS.add('q');
        CHARACTERS.add('r');
        CHARACTERS.add('s');
        CHARACTERS.add('t');
        CHARACTERS.add('u');
        CHARACTERS.add('v');
        CHARACTERS.add('x');
        CHARACTERS.add('w');
        CHARACTERS.add('y');
        CHARACTERS.add('z');
        NUMBERS.add('0');
        NUMBERS.add('1');
        NUMBERS.add('2');
        NUMBERS.add('3');
        NUMBERS.add('4');
        NUMBERS.add('5');
        NUMBERS.add('6');
        NUMBERS.add('7');
        NUMBERS.add('8');
        NUMBERS.add('9');
    }

    public Application() {
    }

    @Override
    protected void init() {
        getSecuritySettings().setUnauthorizedComponentInstantiationListener(this);
        AuthorizationStrategy authorizationStrategy = Spring.getBean(AuthorizationStrategy.class);
        getExceptionSettings().setAjaxErrorHandlingStrategy(ExceptionSettings.AjaxErrorStrategy.REDIRECT_TO_ERROR_PAGE);
        getExceptionSettings().setThreadDumpStrategy(ExceptionSettings.ThreadDumpStrategy.THREAD_HOLDING_LOCK);
        getExceptionSettings().setUnexpectedExceptionDisplay(ExceptionSettings.SHOW_EXCEPTION_PAGE);
        getSecuritySettings().setAuthorizationStrategy(authorizationStrategy);
        getRequestCycleSettings().setBufferResponse(true);
        getRequestCycleSettings().setGatherExtendedBrowserInfo(true);
        getMarkupSettings().setCompressWhitespace(true);
        getMarkupSettings().setDefaultMarkupEncoding("UTF-8");
        getMarkupSettings().setStripWicketTags(true);
        getMarkupSettings().setStripComments(true);
        getApplicationSettings().setClassResolver(Spring.getBean(ClassResolver.class));
        getJavaScriptLibrarySettings().setJQueryReference(new DynamicJQueryResourceReference());
        initLayout();
        initPageMount();
        initService();
    }

    @Override
    protected Class<? extends AbstractAuthenticatedWebSession> getWebSessionClass() {
        return Session.class;
    }

    @Override
    protected Class<? extends WebPage> getSignInPageClass() {
        return LoginPage.class;
    }

    @Override
    public Class<? extends Page> getHomePage() {
        return DashboardPage.class;
    }

    protected void initService() {
        GroovyClassLoader classLoader = Spring.getBean(GroovyClassLoader.class);
        DSLContext context = Spring.getBean(DSLContext.class);
        RestTable restTable = Tables.REST.as("restTable");
        GroovyTable groovyTable = Tables.GROOVY.as("groovyTable");
        List<RestPojo> rests = context.select(restTable.fields()).from(restTable).fetchInto(RestPojo.class);
        for (RestPojo rest : rests) {
            GroovyPojo groovy = context.select(groovyTable.fields()).from(groovyTable).where(groovyTable.GROOVY_ID.eq(rest.getGroovyId())).fetchOneInto(GroovyPojo.class);
            GroovyCodeSource source = new GroovyCodeSource(groovy.getScript(), groovy.getGroovyId(), "/groovy/script");
            source.setCachable(true);
            try {
                classLoader.parseClass(source, true);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    protected void initLayout() {
        GroovyClassLoader classLoader = Spring.getBean(GroovyClassLoader.class);
        DSLContext context = Spring.getBean(DSLContext.class);
        LayoutTable layoutTable = Tables.LAYOUT.as("layoutTable");
        GroovyTable groovyTable = Tables.GROOVY.as("groovyTable");
        List<LayoutPojo> layouts = context.select(layoutTable.fields()).from(layoutTable).fetchInto(LayoutPojo.class);
        for (LayoutPojo layout : layouts) {
            if (!layout.getSystem()) {
                GroovyPojo groovy = context.select(groovyTable.fields()).from(groovyTable).where(groovyTable.GROOVY_ID.eq(layout.getGroovyId())).fetchOneInto(GroovyPojo.class);
                GroovyCodeSource source = new GroovyCodeSource(groovy.getScript(), groovy.getGroovyId(), "/groovy/script");
                source.setCachable(true);
                try {
                    classLoader.parseClass(source, true);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }
    }

    protected void initPageMount() {
        GroovyClassLoader classLoader = Spring.getBean(GroovyClassLoader.class);
        DSLContext context = Spring.getBean(DSLContext.class);
        PageTable pageTable = Tables.PAGE.as("pageTable");
        GroovyTable groovyTable = Tables.GROOVY.as("groovyTable");
        List<PagePojo> pages = context.select(pageTable.fields()).from(pageTable).fetchInto(PagePojo.class);
        for (PagePojo page : pages) {
            if (!page.getCmsPage()) {
                try {
                    mountPage(page.getPath(), (Class<WebPage>) Class.forName(page.getPageId()));
                } catch (ClassNotFoundException e) {
                    throw new WicketRuntimeException(e);
                }
            } else {
                GroovyPojo groovy = context.select(groovyTable.fields()).from(groovyTable).where(groovyTable.GROOVY_ID.eq(page.getGroovyId())).fetchOneInto(GroovyPojo.class);
                GroovyCodeSource source = new GroovyCodeSource(groovy.getScript(), groovy.getGroovyId(), "/groovy/script");
                source.setCachable(true);
                try {
                    Class<?> pageClass = classLoader.parseClass(source, true);
                    mountPage(page.getPath(), (Class<? extends Page>) pageClass);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }
    }
}