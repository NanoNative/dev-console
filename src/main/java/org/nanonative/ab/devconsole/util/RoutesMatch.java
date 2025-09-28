package org.nanonative.ab.devconsole.util;

sealed public interface RoutesMatch permits DevInfo, DevLogs, DevConfig, DevEvents, DevHtml, DevUi, NoMatch {}
