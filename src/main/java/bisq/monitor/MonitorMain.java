/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.monitor;

import bisq.core.app.BisqEnvironment;
import bisq.core.app.BisqExecutable;
import bisq.core.app.HeadlessExecutable;

import bisq.common.UserThread;
import bisq.common.app.AppModule;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

import com.google.inject.Injector;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;
import static spark.Spark.port;



import spark.Spark;

@Slf4j
public class MonitorMain extends HeadlessExecutable {
    private static Monitor monitor;
    private MonitorEnvironment monitorEnvironment;

    public static void main(String[] args) throws Exception {
        // We don't want to do the full argument parsing here as that might easily change in update versions
        // So we only handle the absolute minimum which is APP_NAME, APP_DATA_DIR_KEY and USER_DATA_DIR
        BisqEnvironment.setDefaultAppName("bisq_seednode_monitor");
        if (BisqExecutable.setupInitialOptionParser(args)) {
            // For some reason the JavaFX launch process results in us losing the thread context class loader: reset it.
            // In order to work around a bug in JavaFX 8u25 and below, you must include the following code as the first line of your realMain method:
            Thread.currentThread().setContextClassLoader(MonitorMain.class.getClassLoader());

            new MonitorMain().execute(args);
        }
    }

    private static MonitorEnvironment getEnvironment(OptionSet options) {
        return new MonitorEnvironment(checkNotNull(options));
    }

    @Override
    protected void customizeOptionParsing(OptionParser parser) {
        super.customizeOptionParsing(parser);

        parser.accepts(MonitorOptionKeys.SLACK_URL_SEED_CHANNEL,
                description("Set slack secret for seed node monitor", ""))
                .withRequiredArg();
        parser.accepts(MonitorOptionKeys.SLACK_BTC_SEED_CHANNEL,
                description("Set slack secret for Btc node monitor", ""))
                .withRequiredArg();
        parser.accepts(MonitorOptionKeys.SLACK_PROVIDER_SEED_CHANNEL,
                description("Set slack secret for provider node monitor", ""))
                .withRequiredArg();
        parser.accepts(MonitorOptionKeys.PORT,
                description("Set port to listen on", "8080"))
                .withRequiredArg();
    }

    @Override
    protected void doExecute(OptionSet options) {
        super.doExecute(options);

        checkMemory(monitorEnvironment, monitor);

        String port = monitorEnvironment.getProperty(MonitorOptionKeys.PORT);

        startHttpServer(port);

        keepRunning();
    }

    @Override
    protected void setupEnvironment(OptionSet options) {
        monitorEnvironment = getEnvironment(options);
        Monitor.setMonitorEnvironment(monitorEnvironment);
    }

    @Override
    protected void launchApplication() {
        UserThread.execute(() -> {
            try {
                monitor = new Monitor();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    protected AppModule getModule() {
        //TODO not impl yet
        return null;
    }

    @Override
    protected Injector getInjector() {
        //TODO not impl yet
        return null;
    }

    private void startHttpServer(String port) {
        port(Integer.parseInt(port));
        Spark.get("/", (req, res) -> {
            log.info("Incoming request from: " + req.userAgent());
            return monitor.getMetricsModel().getResultAsHtml();
        });
    }
}
