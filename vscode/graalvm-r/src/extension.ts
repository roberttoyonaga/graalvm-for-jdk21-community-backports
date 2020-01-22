/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

import * as vscode from 'vscode';
import * as cp from 'child_process';
import * as fs from 'fs';
import * as path from 'path';

const INSTALL_GRAALVM_R_COMPONENT: string = 'Install GraalVM R Component';
const INSTALL_R_LANGUAGE_SERVER: string = 'Install R Language Server';
const R_LANGUAGE_SERVER_PACKAGE_NAME: string = 'languageserver';

export function activate(context: vscode.ExtensionContext) {
	context.subscriptions.push(vscode.commands.registerCommand('extension.graalvm-r.installRLanguageServer', () => {
		installRPackage(R_LANGUAGE_SERVER_PACKAGE_NAME);
	}));
	context.subscriptions.push(vscode.debug.registerDebugConfigurationProvider('graalvm', new GraalVMRConfigurationProvider()));
	context.subscriptions.push(vscode.workspace.onDidChangeConfiguration(e => {
		if (e.affectsConfiguration('graalvm.home')) {
			config();
		}
	}));
	config();
}

export function deactivate() {}

function config() {
	const graalVMHome = vscode.workspace.getConfiguration('graalvm').get('home') as string;
	if (graalVMHome) {
		const executable: string = path.join(graalVMHome, 'bin', 'R');
		if (!fs.existsSync(executable)) {
			vscode.window.showInformationMessage('R component is not installed in your GraalVM.', INSTALL_GRAALVM_R_COMPONENT).then(value => {
				switch (value) {
					case INSTALL_GRAALVM_R_COMPONENT:
						vscode.commands.executeCommand('extension.graalvm.installGraalVMComponent', 'R');
						const watcher:fs.FSWatcher = fs.watch(path.join(graalVMHome, 'bin'), () => {
							setConfig(executable);
							watcher.close();
						});
						break;
				}
			});	
		} else {
			setConfig(executable);
		}
	}
}

function setConfig(path: string) {
	const config = vscode.workspace.getConfiguration('r');
	let section: string = '';
	if (process.platform === 'linux') {
		section = 'rterm.linux';
	} else if (process.platform === 'darwin') {
		section = 'rterm.mac';
	}
	const term = section ? config.inspect(section) : undefined;
	if (term) {
		config.update(section, path, true);
	}
	let termArgs = config.get('rterm.option') as string[];
	if (termArgs.indexOf('--inspect') < 0) {
		termArgs.push('--inspect');
		termArgs.push('--inspect.Suspend=false');
		config.update('rterm.option', termArgs, true);
	}
	if (!isRPackageInstalled(R_LANGUAGE_SERVER_PACKAGE_NAME)) {
		vscode.window.showInformationMessage('Language Server package is not installed in your GraalVM R.', INSTALL_R_LANGUAGE_SERVER).then(value => {
			switch (value) {
				case INSTALL_R_LANGUAGE_SERVER:
					installRPackage(R_LANGUAGE_SERVER_PACKAGE_NAME);
					break;
			}
		});
	}
}

function isRPackageInstalled(name: string): boolean {
	const graalVMHome = vscode.workspace.getConfiguration('graalvm').get('home') as string;
	if (graalVMHome) {
		const executable: string = path.join(graalVMHome, 'bin', 'R');
		if (executable) {
			const out = cp.execFileSync(executable, ['--quiet', '--slave', '-e', `ip<-installed.packages();is.element("${name}",ip[,1])`], { encoding: 'utf8' });
			if (out.includes('TRUE')) {
				return true;
			}
		}
	}
	return false;
}

function installRPackage(name: string) {
	const graalVMHome = vscode.workspace.getConfiguration('graalvm').get('home') as string;
	if (graalVMHome) {
		const executable: string = path.join(graalVMHome, 'bin', 'R');
		if (executable) {
            let terminal: vscode.Terminal | undefined = vscode.window.activeTerminal;
            if (!terminal) {
                terminal = vscode.window.createTerminal();
            }
            terminal.show();
            terminal.sendText(`${executable.replace(/(\s+)/g, '\\$1')} --quiet --slave -e 'install.packages("${name}")'`);
		}
	}
	return false;
}

class GraalVMRConfigurationProvider implements vscode.DebugConfigurationProvider {

	resolveDebugConfiguration(_folder: vscode.WorkspaceFolder | undefined, config: vscode.DebugConfiguration, _token?: vscode.CancellationToken): vscode.ProviderResult<vscode.DebugConfiguration> {
		if (config.request === 'launch' && config.name === 'Launch R Term') {
			vscode.commands.executeCommand('r.createRTerm');
			config.request = 'attach';
		}
		return config;
	}
}
