package org.nanonative.ab.devconsole.util;

sealed public interface RoutesMatch permits DevInfo, DevEvents, DevLogs, DevConfig, DevHtml, DevUiFile, NoMatch {}


