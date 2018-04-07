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

import bisq.monitor.metrics.MetricsModel;
import bisq.monitor.metrics.p2p.MonitorP2PService;

import bisq.core.arbitration.ArbitratorManager;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.WalletsSetup;
import bisq.core.offer.OpenOfferManager;
import bisq.core.setup.CoreSetup;

import bisq.common.UserThread;
import bisq.common.handlers.ResultHandler;
import bisq.common.setup.CommonSetup;

import com.google.inject.Guice;
import com.google.inject.Injector;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Monitor {
    public static final String VERSION = "1.0.1";

    private static MonitorEnvironment monitorEnvironment;
    @Getter
    private final MetricsModel metricsModel;

    public static void setMonitorEnvironment(MonitorEnvironment monitorEnvironment) {
        Monitor.monitorEnvironment = monitorEnvironment;
    }

    private final Injector injector;
    private final MonitorModule monitorModule;

    public Monitor() {
        CommonSetup.setup((throwable, doShutDown) -> {
            log.error(throwable.toString());
        });
        CoreSetup.setup(monitorEnvironment);
        log.info("Monitor.VERSION: " + VERSION);

        monitorModule = new MonitorModule(monitorEnvironment);
        injector = Guice.createInjector(monitorModule);

        metricsModel = injector.getInstance(MetricsModel.class);

        MonitorAppSetup appSetup = injector.getInstance(MonitorAppSetup.class);
        appSetup.start();
    }

    private void shutDown() {
        gracefulShutDown(() -> {
            log.debug("Shutdown complete");
            System.exit(0);
        });
    }

    public void gracefulShutDown(ResultHandler resultHandler) {
        log.debug("gracefulShutDown");
        try {
            if (injector != null) {
                injector.getInstance(ArbitratorManager.class).shutDown();
                injector.getInstance(OpenOfferManager.class).shutDown(() -> injector.getInstance(MonitorP2PService.class).shutDown(() -> {
                    injector.getInstance(WalletsSetup.class).shutDownComplete.addListener((ov, o, n) -> {
                        monitorModule.close(injector);
                        log.debug("Graceful shutdown completed");
                        resultHandler.handleResult();
                    });
                    injector.getInstance(WalletsSetup.class).shutDown();
                    injector.getInstance(BtcWalletService.class).shutDown();
                    injector.getInstance(BsqWalletService.class).shutDown();
                }));
                // we wait max 5 sec.
                UserThread.runAfter(resultHandler::handleResult, 5);
            } else {
                UserThread.runAfter(resultHandler::handleResult, 1);
            }
        } catch (Throwable t) {
            log.debug("App shutdown failed with exception");
            t.printStackTrace();
            System.exit(1);
        }
    }
}
