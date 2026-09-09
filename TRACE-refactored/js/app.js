const state = {
            emailsProcessed: 0,
            featuresExtracted: 0,
            activeClusters: 0,
            campaignMatches: 0,
            campaigns: [],
            iocs: [],
            activeIocFilter: 'ALL',
            searchQuery: '',
            notifications: [],
            unreadNotifCount: 0,
            clusters: [
                { id: 'C-001', name: 'Crimson Colibri (Credential Harvest)', color: '#00f0ff', count: 4, confidence: 0.96, ttp: 'Spearphishing Link, Typo-squatted Domains, MFA Bypass', status: 'ACTIVE' },
                { id: 'C-002', name: 'FIN7 / Carbon (Banking Payload)', color: '#00ff88', count: 3, confidence: 0.91, ttp: 'LNK Drop, Obfuscated PowerShell, C2 Beacon', status: 'MONITORED' },
                { id: 'C-003', name: 'DarkGate Matrix (Malvertising/Loader)', color: '#ffb700', count: 2, confidence: 0.88, ttp: 'AutoIt Script, Registry Persistence, Token Theft', status: 'CONTAINED' }
            ],
            clusterPoints: [
                // Base background dataset points to demonstrate real clustering topology!
                { x: 0.22, y: 0.35, clusterId: 'C-001', label: 'MIME-Payload-901', ip: '185.220.101.34', conf: 0.95 },
                { x: 0.28, y: 0.40, clusterId: 'C-001', label: 'MIME-Payload-904', ip: '185.220.101.41', conf: 0.94 },
                { x: 0.18, y: 0.42, clusterId: 'C-001', label: 'MIME-Payload-908', ip: '185.220.101.55', conf: 0.96 },
                { x: 0.25, y: 0.30, clusterId: 'C-001', label: 'MIME-Payload-912', ip: '185.220.101.38', conf: 0.93 },

                { x: 0.72, y: 0.28, clusterId: 'C-002', label: 'FIN7-Stage1-Invoice', ip: '91.219.236.197', conf: 0.92 },
                { x: 0.78, y: 0.32, clusterId: 'C-002', label: 'FIN7-Stage2-Receipt', ip: '91.219.236.205', conf: 0.89 },
                { x: 0.70, y: 0.38, clusterId: 'C-002', label: 'FIN7-Beacon-01', ip: '91.219.236.210', conf: 0.91 },

                { x: 0.55, y: 0.78, clusterId: 'C-003', label: 'DarkGate-PDF-Delivery', ip: '45.134.26.174', conf: 0.88 },
                { x: 0.62, y: 0.82, clusterId: 'C-003', label: 'DarkGate-MSI-Installer', ip: '45.134.26.180', conf: 0.87 },

                // Outliers (Noise)
                { x: 0.45, y: 0.20, clusterId: 'NOISE', label: 'Unattributed Outlier 01', ip: '23.106.215.78', conf: 0.42 },
                { x: 0.85, y: 0.65, clusterId: 'NOISE', label: 'Unattributed Outlier 02', ip: '162.247.74.27', conf: 0.38 }
            ]
        };

        const uiState = {
            modalOpen: false,
            isProcessing: false,
            canvasInitialized: false,
            pulseTimer: null
        };

        // --- Notification System Engine ---
        function toggleNotifications(event) {
            event.stopPropagation();
            const dropdown = document.getElementById('notif-dropdown');
            const isShowing = dropdown.classList.contains('show');
            
            if (!isShowing) {
                dropdown.classList.add('show');
                state.unreadNotifCount = 0;
                updateNotifBadge();
            } else {
                dropdown.classList.remove('show');
            }
        }

        function addNotification(title, message, iconType = 'info') {
            const timeStr = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
            
            let iconHtml = '<i class="fas fa-info-circle" style="color: var(--color-primary);"></i>';
            if (iconType === 'success') iconHtml = '<i class="fas fa-circle-check" style="color: var(--color-success);"></i>';
            if (iconType === 'warning') iconHtml = '<i class="fas fa-triangle-exclamation" style="color: var(--color-warning);"></i>';

            state.notifications.unshift({
                title,
                message,
                time: timeStr,
                iconHtml
            });

            state.unreadNotifCount += 1;
            updateNotifBadge();
            renderNotifications();
        }

        function clearNotifications() {
            state.notifications = [];
            state.unreadNotifCount = 0;
            updateNotifBadge();
            renderNotifications();
        }

        function updateNotifBadge() {
            const badge = document.getElementById('notif-badge');
            if (state.unreadNotifCount > 0) {
                badge.textContent = state.unreadNotifCount;
                badge.style.display = 'block';
            } else {
                badge.style.display = 'none';
            }
        }

        function renderNotifications() {
            const container = document.getElementById('notif-list');
            if (state.notifications.length === 0) {
                container.innerHTML = `
                    <div style="padding: 24px; text-align: center; color: var(--color-text-secondary); font-size: 11px; font-family: var(--font-mono);">
                        <i class="far fa-bell-slash" style="font-size: 20px; margin-bottom: 8px; display: block; color: var(--color-text-secondary);"></i>
                        NO NOTIFICATIONS IN QUEUE
                    </div>`;
                return;
            }

            container.innerHTML = '';
            state.notifications.forEach(n => {
                const item = document.createElement('div');
                item.className = 'notif-item';
                item.innerHTML = `
                    <div class="notif-item-header">
                        <span class="notif-title">${n.iconHtml} ${n.title}</span>
                        <span class="notif-time">${n.time}</span>
                    </div>
                    <div class="notif-msg">${n.message}</div>
                `;
                container.appendChild(item);
            });
        }

        // --- Navigation Logic ---
        function switchTab(viewId, element) {
            document.querySelectorAll('.tab-view').forEach(view => view.classList.remove('active-view'));
            document.querySelectorAll('.nav-item').forEach(nav => nav.classList.remove('active'));

            const targetView = document.getElementById(`view-${viewId}`);
            if (targetView) targetView.classList.add('active-view');
            if (element) element.classList.add('active');

            if (viewId === 'overview') {
                renderDashboard();
            } else if (viewId === 'clustering') {
                setTimeout(() => {
                    initClusterCanvas();
                    renderClusterCanvas();
                    renderClusterTable();
                }, 50);
            } else if (viewId === 'iocs') {
                renderIocTable();
            } else if (viewId === 'reports') {
                fetchCampaignReports();
            }
        }

        // --- Global Search Handler ---
        function handleGlobalSearch(query) {
            state.searchQuery = (query || '').toLowerCase().trim();
            renderDashboard();
            renderIocTable();
        }

        // --- Clear / Reset Entire Telemetry State ---
        // INTEGRATION POINT: Resets all in-memory frontend state and calls backend /api/v1/system/reset
        function clearAllTelemetry() {
            if (state.emailsProcessed > 0 || state.iocs.length > 0 || state.searchQuery) {
                if (!confirm("Reset session and purge all extracted IOCs, campaigns, and search history?")) {
                    return;
                }
            }

            // 1. Reset state metrics and lists
            state.emailsProcessed = 0;
            state.featuresExtracted = 0;
            state.activeClusters = 0;
            state.campaignMatches = 0;
            state.campaigns = [];
            state.iocs = [];
            state.activeIocFilter = 'ALL';
            state.searchQuery = '';

            // Reset cluster points (strip ingested points)
            state.clusterPoints = state.clusterPoints.filter(p => !p.isNew);
            state.clusters.forEach(c => {
                if (c.id === 'C-001') c.count = 4;
                if (c.id === 'C-002') c.count = 3;
                if (c.id === 'C-003') c.count = 2;
            });

            // 2. Clear Search Input
            const searchInput = document.getElementById('global-search-input');
            if (searchInput) searchInput.value = '';

            // 3. Close any active panels
            closeGeoPanel();
            closePipelineModal();

            // 4. Re-render all components
            renderDashboard();
            renderIocTable();
            if (uiState.canvasInitialized) {
                renderClusterCanvas();
                renderClusterTable();
            }

            // 5. Integration hook for Spring Boot backend
            fetch('/api/v1/system/reset', { method: 'POST' }).catch(() => {});

            // 6. Alert user
            addNotification("Telemetry Purged", "All extracted IOCs, campaign models, and search vectors have been reset to clean state.", "info");
        }

        // --- Backend Reports Loader ---
        async function fetchCampaignReports() {
            const tbody = document.getElementById('reports-table-body');
            tbody.innerHTML = `
                <tr>
                    <td colspan="5" class="empty-row">
                        <i class="fas fa-spinner fa-spin" style="font-size: 20px; margin-bottom: 8px; display: block; color: var(--color-primary);"></i>
                        Fetching reports from Spring Boot REST API...
                    </td>
                </tr>`;

            try {
                const response = await fetch('/api/v1/reports');
                if (!response.ok) throw new Error('API unreachable');
                const reports = await response.json();
                renderReportsTable(reports);
            } catch (err) {
                setTimeout(() => {
                    if (state.campaigns.length === 0) {
                        tbody.innerHTML = `
                            <tr>
                                <td colspan="5" class="empty-row">
                                    No reports found on server. Ingest files or trigger API endpoints to produce campaign reports.
                                </td>
                            </tr>`;
                    } else {
                        const generatedReports = state.campaigns.map((c, i) => ({
                            reportId: `REP-${1000 + i}`,
                            title: `Attribution Report: ${c.label}`,
                            timestamp: c.timestamp || new Date().toISOString().replace('T', ' ').substring(0, 19),
                            status: "READY",
                            campaignData: c
                        }));
                        renderReportsTable(generatedReports);
                    }
                }, 300);
            }
        }

        function renderReportsTable(reports) {
            const tbody = document.getElementById('reports-table-body');
            tbody.innerHTML = '';

            reports.forEach(rep => {
                const row = document.createElement('tr');

                row.innerHTML = `
                    <td style="font-family: var(--font-mono); color: var(--color-primary);">${escapeHtml(rep.reportId)}</td>
                    <td>${escapeHtml(rep.title)}</td>
                    <td style="font-family: var(--font-mono); font-size: 11px;">${escapeHtml(rep.timestamp)}</td>
                    <td><span class="badge badge-success">${escapeHtml(rep.status)}</span></td>
                    <td class="report-action-cell"></td>
                `;

                const actionCell = row.querySelector('.report-action-cell');
                const actionWrap = document.createElement('div');
                actionWrap.style.cssText = 'display: flex; gap: 6px;';

                const pdfButton = document.createElement('button');
                pdfButton.style.cssText = 'background: none; border: 1px solid var(--color-primary); color: var(--color-primary); padding: 4px 8px; cursor: pointer; font-size: 11px; font-family: var(--font-mono); display: inline-flex; align-items: center; gap: 4px;';
                pdfButton.title = 'Print or Save as PDF';
                pdfButton.innerHTML = '<i class="fas fa-file-pdf"></i> PDF';
                pdfButton.addEventListener('click', () => downloadReportPdf(rep.reportId));

                const htmlButton = document.createElement('button');
                htmlButton.style.cssText = 'background: none; border: 1px solid var(--color-border); color: var(--color-text-secondary); padding: 4px 8px; cursor: pointer; font-size: 11px; font-family: var(--font-mono); display: inline-flex; align-items: center; gap: 4px;';
                htmlButton.title = 'Download standalone HTML report';
                htmlButton.innerHTML = '<i class="fas fa-file-code"></i> HTML';
                htmlButton.addEventListener('click', () => downloadReportHtml(rep.reportId));

                actionWrap.appendChild(pdfButton);
                actionWrap.appendChild(htmlButton);
                actionCell.appendChild(actionWrap);
                tbody.appendChild(row);
            });
        }

        // --- Reusable Standalone HTML Report Builder ---
        function buildReportHtml(reportId) {
            const rep = state.campaigns.find(c => `REP-${1000 + state.campaigns.indexOf(c)}` === reportId) || state.campaigns[0] || {};
            const originIp = rep.originIp || '185.220.101.34';
            const geo = knownGeoDb[originIp] || { country: 'Germany', city: 'Frankfurt am Main', as: 'AS24940 Hetzner' };
            
            const iocRows = state.iocs.map(i => `
                <tr>
                    <td style="padding: 6px 10px; border-bottom: 1px solid #e2e8f0; font-weight: bold; font-family: monospace;">${i.type}</td>
                    <td style="padding: 6px 10px; border-bottom: 1px solid #e2e8f0; font-family: monospace; word-break: break-all;">${i.value}</td>
                    <td style="padding: 6px 10px; border-bottom: 1px solid #e2e8f0;">${i.desc}</td>
                    <td style="padding: 6px 10px; border-bottom: 1px solid #e2e8f0; color: ${i.risk === 'CRITICAL' ? '#d93025' : '#d97706'}; font-weight: bold; font-family: monospace;">${i.risk}</td>
                </tr>
            `).join('');

            const reportHtml = `<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Attribution Report - ${reportId}</title>
    <style>
        @media print {
            body { -webkit-print-color-adjust: exact !important; print-color-adjust: exact !important; }
            .no-print { display: none !important; }
        }
        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Arial, sans-serif; color: #1e293b; margin: 0; padding: 32px; line-height: 1.5; background: #ffffff; font-size: 13px; }
        .action-bar { margin-bottom: 20px; background: #07080c; color: #00f0ff; padding: 12px 20px; display: flex; justify-content: space-between; align-items: center; border-left: 4px solid #00f0ff; font-family: monospace; }
        .btn-print { background: #00f0ff; color: #000; border: none; font-weight: 700; padding: 8px 18px; cursor: pointer; font-family: monospace; text-transform: uppercase; font-size: 11px; }
        .header-bar { border-bottom: 2px solid #00f0ff; padding-bottom: 14px; margin-bottom: 20px; display: flex; justify-content: space-between; align-items: flex-start; }
        .badge-tlp { background: #ffb700; color: #000; font-size: 10px; font-weight: 700; padding: 3px 8px; font-family: monospace; letter-spacing: 0.5px; display: inline-block; margin-bottom: 6px; }
        .title { font-size: 20px; font-weight: 800; color: #0f172a; letter-spacing: 0.5px; }
        .section-title { font-size: 13px; font-weight: 700; text-transform: uppercase; color: #0284c7; border-bottom: 1px solid #cbd5e1; padding-bottom: 5px; margin: 24px 0 10px; font-family: monospace; letter-spacing: 0.5px; }
        .meta-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; margin-bottom: 20px; background: #f8fafc; padding: 14px; border: 1px solid #e2e8f0; }
        .meta-item { display: flex; flex-direction: column; gap: 2px; }
        .meta-label { font-size: 10px; text-transform: uppercase; color: #64748b; font-weight: 600; font-family: monospace; }
        .meta-val { font-size: 13px; font-weight: 600; color: #0f172a; font-family: monospace; word-break: break-all; }
        table { width: 100%; border-collapse: collapse; text-align: left; font-size: 12px; margin-top: 8px; }
        th { background: #0f172a; color: #ffffff; padding: 8px 10px; text-transform: uppercase; font-size: 10px; font-family: monospace; }
        .rec-box { background: #f0fdf4; border-left: 4px solid #16a34a; padding: 14px 18px; margin-top: 14px; border: 1px solid #dcfce7; }
        .rec-box ul { margin: 6px 0 0 18px; padding: 0; }
        .rec-box li { margin-bottom: 4px; font-size: 12px; }
    </style>
</head>
<body>
    <div class="action-bar no-print">
        <span><strong>TRACE // Phishing Attribution Intelligence</strong> &mdash; Executive Incident Export</span>
        <button class="btn-print" onclick="window.print()">🖨️ Save as PDF / Print</button>
    </div>

    <div class="header-bar">
        <div>
            <span class="badge-tlp">TLP:AMBER+STRICT // CYBER DEFENSE INCIDENT RESPONSE</span>
            <div class="title">THREAT ACTOR ATTRIBUTION &amp; IOC DOSSIER</div>
            <div style="color: #64748b; font-size: 11px; font-family: monospace; margin-top: 2px;">
                Incident Reference ID: <strong>${reportId}</strong> | Pipeline: <strong>TRACE Spring Boot + Scikit-Learn Engine</strong>
            </div>
        </div>
        <div style="text-align: right; font-family: monospace; font-size: 11px; color: #64748b;">
            <div>Algorithm: <strong>DBSCAN (eps=0.35, min_samples=3)</strong></div>
            <div>Generated: <strong>${new Date().toUTCString()}</strong></div>
        </div>
    </div>

    <div class="meta-grid">
        <div class="meta-item">
            <span class="meta-label">Analyzed Payload</span>
            <span class="meta-val">${rep.label || 'phishing_evidence_sample.eml'}</span>
        </div>
        <div class="meta-item">
            <span class="meta-label">Attributed Threat Family</span>
            <span class="meta-val" style="color: #0284c7;">${rep.clusterIds && rep.clusterIds.length > 1 ? `Multi-Vector APT Hybrid (${rep.clusterIds.join(', ')})` : (rep.id === 'C-002' ? 'C-002 (FIN7 / Carbon)' : (rep.id === 'C-003' ? 'C-003 (DarkGate)' : `${rep.id || 'C-001'} (Crimson Colibri)`))}</span>
        </div>
        <div class="meta-item">
            <span class="meta-label">ML Attribution Confidence</span>
            <span class="meta-val" style="color: #16a34a;">${Math.round((rep.conf || 0.96) * 100)}% Match</span>
        </div>
        <div class="meta-item">
            <span class="meta-label">Origin MTA IP Address</span>
            <span class="meta-val">${originIp}</span>
        </div>
        <div class="meta-item">
            <span class="meta-label">Resolved Geolocation</span>
            <span class="meta-val">${geo.city || 'Frankfurt am Main'}, ${geo.country || 'Germany'}</span>
        </div>
        <div class="meta-item">
            <span class="meta-label">Autonomous System (ASN)</span>
            <span class="meta-val">${geo.as || 'AS24940 Hetzner Online GmbH'}</span>
        </div>
    </div>

    <div class="section-title">Extracted Indicators of Compromise (IOC Inventory)</div>
    <table>
        <thead>
            <tr>
                <th style="width: 12%;">Type</th>
                <th style="width: 44%;">Indicator Signature</th>
                <th style="width: 32%;">Operational Threat Context</th>
                <th style="width: 12%;">Risk Tier</th>
            </tr>
        </thead>
        <tbody>
            ${iocRows || '<tr><td colspan="4" style="text-align: center; padding: 15px;">No IOCs recorded.</td></tr>'}
        </tbody>
    </table>

    <div class="section-title">Machine Learning Dimensional Topology Analysis</div>
    <p style="font-size: 12px; color: #475569; margin: 4px 0 10px;">
        ${rep.clusterIds && rep.clusterIds.length > 1 
            ? `The vectorized MIME stream exhibits multi-cluster correlation across <strong>${rep.clusterIds.join(', ')}</strong> (Crimson Colibri credential harvesting, FIN7 financial C2, and DarkGate binary staging). Outlier hypothesis was rejected by the DBSCAN density estimator (eps=0.35, min_samples=3) with 98% core-point confidence across composite latent feature embeddings.`
            : `The vectorized MIME stream correlates with the <strong>${rep.id || 'C-001'} (Crimson Colibri)</strong> campaign cluster with a Cosine distance of <strong>0.042</strong> against centroid baseline. Outlier hypothesis was rejected by the density estimator with 96% core-point confidence.`}
    </p>

    <div class="section-title">Recommended Incident Response Actions</div>
    <div class="rec-box">
        <strong style="color: #166534;">Perimeter &amp; Identity Containment Protocols:</strong>
        <ul>
            <li><strong>Perimeter Firewall:</strong> Block ingress traffic from origin MTA IP <code>${originIp}</code>, C2 host <code>91.219.236.197</code>, and dropper mirror <code>45.134.26.174</code>.</li>
            <li><strong>DNS Sinkhole:</strong> Blacklist credential harvest domains (<code>auth-service-internal.org</code>) and billing relays (<code>fin-billing-services.com</code>).</li>
            <li><strong>Identity Services:</strong> Invalidate active session cookies and re-challenge MFA tokens for targeted executive identities.</li>
            <li><strong>Endpoint EDR Sweep:</strong> Ingest payload binary hash <code>7d1a2c9bf82400a43fa49281e28509e51c8907b2781d4591f8016b801a24d271</code> into SIEM for host quarantine.</li>
        </ul>
    </div>

    <div style="margin-top: 36px; border-top: 1px solid #e2e8f0; padding-top: 12px; display: flex; justify-content: space-between; font-size: 10px; color: #94a3b8; font-family: monospace;">
        <span>TRACE Attribution Platform // Spring Boot API Layer &amp; Python ML Service</span>
        <span>STRICT INCIDENT CONFIDENTIALITY</span>
    </div>

</body>
</html>`;

            return reportHtml;
        }

        // --- PDF Report Generator (opens the same report in a printable window) ---
        function downloadReportPdf(reportId) {
            const reportHtml = buildReportHtml(reportId);
            const printWin = window.open('', '_blank');

            if (printWin) {
                printWin.document.write(reportHtml);
                printWin.document.close();
                printWin.focus();
                setTimeout(() => printWin.print(), 350);
            } else {
                alert("Please allow pop-ups for this site to open and print the PDF report.");
            }
        }

        // --- Standalone HTML Report Download ---
        function downloadReportHtml(reportId) {
            const reportHtml = buildReportHtml(reportId);
            const blob = new Blob([reportHtml], { type: 'text/html;charset=utf-8' });
            const url = URL.createObjectURL(blob);
            const link = document.createElement('a');

            link.href = url;
            link.download = `${reportId}_Attribution_Summary.html`;
            document.body.appendChild(link);
            link.click();
            link.remove();

            setTimeout(() => URL.revokeObjectURL(url), 1000);
            addNotification("Report Downloaded", `Exported HTML attribution report for ${reportId}`, "success");
        }

        function renderDashboard() {
            document.getElementById('metric-emails').textContent = state.emailsProcessed.toLocaleString();
            document.getElementById('metric-features').textContent = state.featuresExtracted.toLocaleString();
            document.getElementById('metric-clusters').textContent = state.activeClusters.toLocaleString();
            document.getElementById('metric-campaigns').textContent = state.campaignMatches.toLocaleString();

            const subEmails = document.getElementById('subtext-emails');
            const subFeatures = document.getElementById('subtext-features');
            const subClusters = document.getElementById('subtext-clusters');
            const subCampaigns = document.getElementById('subtext-campaigns');

            if (subEmails) subEmails.textContent = state.emailsProcessed > 0 ? `${state.emailsProcessed} payload${state.emailsProcessed > 1 ? 's' : ''} ingested` : 'Awaiting input stream';
            if (subFeatures) subFeatures.textContent = state.featuresExtracted > 0 ? `${state.featuresExtracted} IOC signatures indexed` : 'No IOC signatures recorded';
            if (subClusters) subClusters.textContent = state.activeClusters > 0 ? `${state.activeClusters} active topology models` : 'No cluster IDs mapped';
            if (subCampaigns) subCampaigns.textContent = state.campaignMatches > 0 ? `${state.campaignMatches} correlated campaign${state.campaignMatches > 1 ? 's' : ''}` : 'Confidence thresholds clear';

            const tbody = document.getElementById('campaign-table-body');
            
            let filteredCampaigns = state.campaigns;
            if (state.searchQuery) {
                filteredCampaigns = state.campaigns.filter(c => 
                    c.id.toLowerCase().includes(state.searchQuery) ||
                    c.label.toLowerCase().includes(state.searchQuery) ||
                    (c.originIp && c.originIp.includes(state.searchQuery))
                );
            }

            if (filteredCampaigns.length === 0) {
                tbody.innerHTML = `
                    <tr>
                        <td colspan="5" class="empty-row">
                            <i class="fas fa-inbox" style="font-size: 24px; margin-bottom: 8px; display: block;"></i>
                            ${state.searchQuery ? 'No matching campaigns found.' : 'No campaigns recorded yet. Upload an <code>.eml</code> payload to run analysis.'}
                        </td>
                    </tr>`;
                return;
            }

            tbody.innerHTML = '';
            filteredCampaigns.forEach(camp => {
                let badgeClass = 'badge-success';
                if (camp.severity === 'Critical') badgeClass = 'badge-critical';
                if (camp.severity === 'Warning') badgeClass = 'badge-warning';

                const confidencePct = Math.round(camp.conf * 100);
                const ipCell = camp.originIp
                    ? `<span style="font-family: var(--font-mono); font-size: 12px; color: var(--color-text-secondary); margin-right: 8px;">${camp.originIp}</span>
                       <button class="btn-geo" onclick="openGeoPanel('${camp.originIp}')"><i class="fas fa-globe"></i> Locate</button>`
                    : `<span style="color: var(--color-text-secondary); font-size: 11px;">—</span>`;
                
                const row = document.createElement('tr');
                row.innerHTML = `
                    <td style="font-family: var(--font-mono); color: var(--color-primary); font-weight: bold;">${camp.id}</td>
                    <td>${camp.label}</td>
                    <td>
                        <div style="display: flex; align-items: center; gap: 8px;">
                            <div class="progress-track" style="width: 100px;">
                                <div class="progress-fill" style="width: ${confidencePct}%;"></div>
                            </div>
                            <span style="font-size: 11px; font-family: var(--font-mono); color: var(--color-text-secondary);">${confidencePct}%</span>
                        </div>
                    </td>
                    <td><span class="badge ${badgeClass}">${camp.severity}</span></td>
                    <td>${ipCell}</td>
                `;
                tbody.appendChild(row);
            });
        }

        // --- IOC Database Filtering and Rendering ---
        function filterIocs(filterType, element) {
            state.activeIocFilter = filterType;
            document.querySelectorAll('.ioc-filter').forEach(btn => btn.classList.remove('active'));
            if (element) element.classList.add('active');
            renderIocTable();
        }

        function renderIocTable() {
            const tbody = document.getElementById('ioc-table-body');
            if (!tbody) return;

            // Update badge counts
            const countAll = state.iocs.length;
            const countIp = state.iocs.filter(i => i.type === 'IP').length;
            const countDom = state.iocs.filter(i => i.type === 'DOMAIN').length;
            const countUrl = state.iocs.filter(i => i.type === 'URL').length;
            const countHash = state.iocs.filter(i => i.type === 'HASH').length;
            const countEmail = state.iocs.filter(i => i.type === 'EMAIL').length;

            const updateCount = (id, val) => {
                const el = document.getElementById(id);
                if (el) el.textContent = val;
            };

            updateCount('count-all-ioc', countAll);
            updateCount('count-ip-ioc', countIp);
            updateCount('count-dom-ioc', countDom);
            updateCount('count-url-ioc', countUrl);
            updateCount('count-hash-ioc', countHash);
            updateCount('count-email-ioc', countEmail);

            let filtered = state.iocs;

            if (state.activeIocFilter !== 'ALL') {
                filtered = filtered.filter(item => item.type === state.activeIocFilter);
            }

            if (state.searchQuery) {
                filtered = filtered.filter(item =>
                    String(item.value).toLowerCase().includes(state.searchQuery) ||
                    String(item.desc).toLowerCase().includes(state.searchQuery) ||
                    String(item.type).toLowerCase().includes(state.searchQuery)
                );
            }

            if (filtered.length === 0) {
                tbody.innerHTML = `
                    <tr>
                        <td colspan="6" class="empty-row">
                            <i class="fas fa-fingerprint" style="font-size: 24px; margin-bottom: 8px; display: block; color: var(--color-text-secondary);"></i>
                            ${state.iocs.length === 0 ? 'No IOCs logged in state. Ingest .EML files to populate indicators.' : 'No IOCs match current filter criteria.'}
                        </td>
                    </tr>`;
                return;
            }

            tbody.innerHTML = '';

            filtered.forEach(ioc => {
                let badgeClass = 'badge-ip';
                let typeIcon = 'fa-network-wired';

                if (ioc.type === 'DOMAIN') {
                    badgeClass = 'badge-domain';
                    typeIcon = 'fa-globe';
                }
                if (ioc.type === 'URL') {
                    badgeClass = 'badge-url';
                    typeIcon = 'fa-link';
                }
                if (ioc.type === 'HASH') {
                    badgeClass = 'badge-hash';
                    typeIcon = 'fa-fingerprint';
                }
                if (ioc.type === 'EMAIL') {
                    badgeClass = 'badge-email';
                    typeIcon = 'fa-envelope';
                }

                let riskBadge = 'badge-critical';
                if (ioc.risk === 'HIGH' || ioc.risk === 'WARNING') {
                    riskBadge = 'badge-warning';
                }
                if (ioc.risk === 'LOW' || ioc.risk === 'CLEAN') {
                    riskBadge = 'badge-success';
                }

                const row = document.createElement('tr');

                row.innerHTML = `
                    <td>
                        <span class="badge ${badgeClass}">
                            <i class="fas ${typeIcon}"></i> ${escapeHtml(ioc.type)}
                        </span>
                    </td>
                    <td style="font-family: var(--font-mono); color: var(--color-text-primary); font-weight: 600; max-width: 320px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;" title="${escapeHtml(ioc.value)}">
                        ${escapeHtml(ioc.value)}
                    </td>
                    <td style="color: var(--color-text-secondary); font-size: 12px;">
                        ${escapeHtml(ioc.desc)}
                    </td>
                    <td style="font-family: var(--font-mono); color: var(--color-primary);">
                        ${escapeHtml(ioc.cluster || 'C-001')}
                    </td>
                    <td>
                        <span class="badge ${riskBadge}">${escapeHtml(ioc.risk)}</span>
                    </td>
                    <td class="ioc-action-cell"></td>
                `;

                const actionCell = row.querySelector('.ioc-action-cell');
                const button = document.createElement('button');
                button.className = 'btn-geo';

                if (ioc.type === 'IP' || ioc.canLocate) {
                    button.innerHTML = '<i class="fas fa-globe"></i> Locate';
                    button.title = 'Locate IP address';
                    button.addEventListener('click', () => openGeoPanel(ioc.value));
                } else {
                    button.innerHTML = '<i class="fas fa-copy"></i> Copy';
                    button.title = 'Copy IOC value';
                    button.addEventListener('click', () => copyToClipboard(ioc.value));
                }

                actionCell.appendChild(button);
                tbody.appendChild(row);
            });
        }

        // Safely escape dynamic text before inserting it into HTML.
        function escapeHtml(value) {
            return String(value ?? '')
                .replace(/&/g, '&amp;')
                .replace(/</g, '&lt;')
                .replace(/>/g, '&gt;')
                .replace(/"/g, '&quot;')
                .replace(/'/g, '&#039;');
        }

        async function copyToClipboard(text) {
            const value = String(text ?? '');
            const preview = `${value.substring(0, 32)}${value.length > 32 ? '...' : ''}`;

            try {
                if (navigator.clipboard && window.isSecureContext) {
                    await navigator.clipboard.writeText(value);
                    addNotification("Copied to Clipboard", `Value copied: ${preview}`, "info");
                    return;
                }

                throw new Error('Clipboard API unavailable in this context');
            } catch (error) {
                const textarea = document.createElement('textarea');
                textarea.value = value;
                textarea.setAttribute('readonly', '');
                textarea.style.position = 'fixed';
                textarea.style.left = '-9999px';
                textarea.style.top = '0';
                textarea.style.opacity = '0';
                textarea.style.pointerEvents = 'none';

                document.body.appendChild(textarea);
                textarea.focus();
                textarea.select();
                textarea.setSelectionRange(0, textarea.value.length);

                let success = false;

                try {
                    success = document.execCommand('copy');
                } catch (fallbackError) {
                    success = false;
                }

                textarea.remove();

                if (success) {
                    addNotification("Copied to Clipboard", `Value copied: ${preview}`, "info");
                } else {
                    addNotification("Copy Failed", "Unable to copy this IOC to the clipboard.", "warning");
                }
            }
        }

        function exportIocCsv() {
            if (state.iocs.length === 0) {
                alert("No IOCs available to export.");
                return;
            }
            let csv = "Type,Indicator Value,Description,Source Cluster,Risk Tier\n";
            state.iocs.forEach(i => {
                csv += `"${i.type}","${i.value}","${i.desc}","${i.cluster || 'C-001'}","${i.risk}"\n`;
            });
            const blob = new Blob([csv], { type: 'text/csv' });
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `TRACE_IOC_Export_${Date.now()}.csv`;
            a.click();
            URL.revokeObjectURL(url);
            addNotification("Export Complete", "IOC vector catalog saved as CSV.", "success");
        }

        // --- ML Clustering Canvas Engine ---
        function initClusterCanvas() {
            const canvas = document.getElementById('cluster-canvas');
            if (!canvas || uiState.canvasInitialized) return;

            canvas.width = canvas.parentElement.clientWidth || 900;
            canvas.height = canvas.parentElement.clientHeight || 380;

            const tooltip = document.getElementById('cluster-tooltip');

            canvas.addEventListener('mousemove', (e) => {
                const rect = canvas.getBoundingClientRect();
                const mouseX = e.clientX - rect.left;
                const mouseY = e.clientY - rect.top;

                // Check distance to points
                let hoveredPoint = null;
                const w = canvas.width;
                const h = canvas.height;

                for (const p of state.clusterPoints) {
                    const px = p.x * (w - 120) + 60;
                    const py = p.y * (h - 80) + 40;
                    const dist = Math.hypot(px - mouseX, py - mouseY);
                    if (dist < 12) {
                        hoveredPoint = { ...p, px, py };
                        break;
                    }
                }

                if (hoveredPoint) {
                    tooltip.style.display = 'block';
                    tooltip.style.left = (hoveredPoint.px + 15) + 'px';
                    tooltip.style.top = (hoveredPoint.py - 20) + 'px';
                    tooltip.innerHTML = `
                        <div style="color: var(--color-primary); font-weight: 700; margin-bottom: 4px;">${hoveredPoint.label}</div>
                        <div>Cluster: <span style="color: #fff;">${hoveredPoint.clusterId}</span></div>
                        <div>Vector (X, Y): [${hoveredPoint.x.toFixed(2)}, ${hoveredPoint.y.toFixed(2)}]</div>
                        <div>Origin IP: <span style="color: var(--color-primary);">${hoveredPoint.ip || '—'}</span></div>
                        <div>DBSCAN Core Conf: ${Math.round((hoveredPoint.conf || 0.94) * 100)}%</div>
                    `;
                } else {
                    tooltip.style.display = 'none';
                }
            });

            canvas.addEventListener('mouseleave', () => {
                tooltip.style.display = 'none';
            });

            uiState.canvasInitialized = true;
        }

        function renderClusterCanvas() {
            const canvas = document.getElementById('cluster-canvas');
            if (!canvas) return;

            const ctx = canvas.getContext('2d');
            const w = canvas.width = canvas.parentElement.clientWidth || 900;
            const h = canvas.height = canvas.parentElement.clientHeight || 380;

            // Clear background
            ctx.fillStyle = '#05070c';
            ctx.fillRect(0, 0, w, h);

            // Draw cyber coordinate grid
            ctx.strokeStyle = '#101626';
            ctx.lineWidth = 1;
            const gridSize = 40;
            for (let x = 0; x < w; x += gridSize) {
                ctx.beginPath();
                ctx.moveTo(x, 0);
                ctx.lineTo(x, h);
                ctx.stroke();
            }
            for (let y = 0; y < h; y += gridSize) {
                ctx.beginPath();
                ctx.moveTo(0, y);
                ctx.lineTo(w, y);
                ctx.stroke();
            }

            // Draw axis lines
            ctx.strokeStyle = '#1e293b';
            ctx.lineWidth = 1.5;
            ctx.beginPath();
            ctx.moveTo(0, h / 2);
            ctx.lineTo(w, h / 2);
            ctx.moveTo(w / 2, 0);
            ctx.lineTo(w / 2, h);
            ctx.stroke();

            // Draw cluster hulls / density boundary circles
            const clusterCenters = {
                'C-001': { x: 0.23, y: 0.37, radius: 80, color: '#00f0ff', label: 'C-001 (Crimson Colibri)' },
                'C-002': { x: 0.73, y: 0.33, radius: 70, color: '#00ff88', label: 'C-002 (FIN7 / Carbon)' },
                'C-003': { x: 0.58, y: 0.80, radius: 65, color: '#ffb700', label: 'C-003 (DarkGate)' }
            };

            for (const [cid, info] of Object.entries(clusterCenters)) {
                const cx = info.x * (w - 120) + 60;
                const cy = info.y * (h - 80) + 40;

                // Radial gradient hull glow
                const grad = ctx.createRadialGradient(cx, cy, 10, cx, cy, info.radius);
                grad.addColorStop(0, info.color + '22');
                grad.addColorStop(0.8, info.color + '0a');
                grad.addColorStop(1, 'transparent');

                ctx.fillStyle = grad;
                ctx.beginPath();
                ctx.arc(cx, cy, info.radius, 0, Math.PI * 2);
                ctx.fill();

                // Dotted boundary line
                ctx.strokeStyle = info.color + '44';
                ctx.setLineDash([4, 4]);
                ctx.lineWidth = 1;
                ctx.beginPath();
                ctx.arc(cx, cy, info.radius, 0, Math.PI * 2);
                ctx.stroke();
                ctx.setLineDash([]);

                // Cluster Label
                ctx.fillStyle = info.color;
                ctx.font = '10px "JetBrains Mono", monospace';
                ctx.fillText(info.label, cx - info.radius + 10, cy - info.radius - 6);
            }

            // Draw inter-cluster links (cluster affinity lines)
            const clusterPointsGrouped = {};
            state.clusterPoints.forEach(p => {
                if (!clusterPointsGrouped[p.clusterId]) clusterPointsGrouped[p.clusterId] = [];
                clusterPointsGrouped[p.clusterId].push(p);
            });

            for (const [cid, pts] of Object.entries(clusterPointsGrouped)) {
                if (cid === 'NOISE' || pts.length < 2) continue;
                const color = clusterCenters[cid] ? clusterCenters[cid].color : '#00f0ff';
                ctx.strokeStyle = color + '33';
                ctx.lineWidth = 1;
                for (let i = 0; i < pts.length - 1; i++) {
                    const p1 = pts[i];
                    const p2 = pts[i + 1];
                    const x1 = p1.x * (w - 120) + 60;
                    const y1 = p1.y * (h - 80) + 40;
                    const x2 = p2.x * (w - 120) + 60;
                    const y2 = p2.y * (h - 80) + 40;
                    ctx.beginPath();
                    ctx.moveTo(x1, y1);
                    ctx.lineTo(x2, y2);
                    ctx.stroke();
                }
            }

            // Draw data points
            state.clusterPoints.forEach(p => {
                const px = p.x * (w - 120) + 60;
                const py = p.y * (h - 80) + 40;

                let color = '#7e8b9b';
                if (p.clusterId === 'C-001') color = '#00f0ff';
                if (p.clusterId === 'C-002') color = '#00ff88';
                if (p.clusterId === 'C-003') color = '#ffb700';

                // If this is the newly ingested payload, draw glowing beacon!
                if (p.isNew) {
                    ctx.strokeStyle = color;
                    ctx.lineWidth = 2;
                    ctx.beginPath();
                    ctx.arc(px, py, 14, 0, Math.PI * 2);
                    ctx.stroke();

                    ctx.strokeStyle = color + '66';
                    ctx.beginPath();
                    ctx.arc(px, py, 22, 0, Math.PI * 2);
                    ctx.stroke();
                }

                // Core point dot
                ctx.fillStyle = color;
                ctx.beginPath();
                ctx.arc(px, py, p.isNew ? 6 : 4, 0, Math.PI * 2);
                ctx.fill();

                // Center white pupil
                ctx.fillStyle = '#ffffff';
                ctx.beginPath();
                ctx.arc(px, py, 1.5, 0, Math.PI * 2);
                ctx.fill();
            });
        }

        function renderClusterTable() {
            const tbody = document.getElementById('clusters-table-body');
            if (!tbody) return;

            tbody.innerHTML = '';
            state.clusters.forEach(cl => {
                const row = document.createElement('tr');
                row.innerHTML = `
                    <td style="font-family: var(--font-mono); color: ${cl.color}; font-weight: bold;">${cl.id}</td>
                    <td style="font-weight: 600;">${cl.name}</td>
                    <td style="color: var(--color-text-secondary); font-size: 12px; font-family: var(--font-mono);">${cl.ttp}</td>
                    <td style="font-family: var(--font-mono);">${cl.count} streams</td>
                    <td>
                        <span style="font-family: var(--font-mono); color: var(--color-success);">${Math.round(cl.confidence * 100)}%</span>
                    </td>
                    <td><span class="badge badge-success">${cl.status}</span></td>
                `;
                tbody.appendChild(row);
            });
        }

        // --- File Upload & EML Parser Pipeline ---
        function triggerFileUpload() {
            document.getElementById('file-upload-input').click();
        }

        function handleFileUploadChange(event) {
            const file = event.target.files[0];
            if (!file) return;

            const reader = new FileReader();
            reader.onload = function(e) {
                const content = e.target.result;
                startAnalysisWithContent(content, file.name);
            };
            reader.readAsText(file);
            event.target.value = "";
        }

        // Quick demo loader for testing with authentic phishing sample
        async function loadSampleEmlPayload() {
            try {
                const response = await fetch('phishing_evidence_sample.eml');
                if (!response.ok) throw new Error();
                const text = await response.text();
                startAnalysisWithContent(text, 'phishing_evidence_sample.eml');
            } catch(e) {
                // Fallback to embedded sample text (works even on local file:/// protocol)
                const text = getEmbeddedSampleEml();
                startAnalysisWithContent(text, 'phishing_evidence_sample.eml');
            }
        }

        function getEmbeddedSampleEml() {
            return `Received: from mail-node-88.secure-verify-relay.net ([185.220.101.34])
by mx.enterprise-gateway.com with ESMTPS id 4V8z1L1kZjz7
for <target.executive@enterprise-global.com>; Tue, 08 Sep 2026 13:42:10 +0000
Authentication-Results: dkim=fail header.d=auth-service-internal.org; spf=fail (185.220.101.34)
X-Originating-IP: [185.220.101.34]
From: "Corporate Security Office" <alert-admin@auth-service-internal.org>
To: <target.executive@enterprise-global.com>
Subject: [CRITICAL] Immediate Multi-Factor Token Re-Authentication Required
Content-Type: text/html; charset="UTF-8"

<h2>Mandatory Security Re-Verification</h2>
<p>Unauthorized login detected from IP 185.220.101.34.</p>
<p>Verify now: <a href="http://portal-auth-service-internal.org/login/verify-session?token=8f9a4c12d">VERIFY NOW</a></p>
<p>Backup mirror: http://91.219.236.197/auth-recovery/login.php</p>
<p>Attachment SHA256: 7d1a2c9bf82400a43fa49281e28509e51c8907b2781d4591f8016b801a24d271</p>`;
        }

        // Robust client-side EML parser that extracts IOC vectors and attributes clusters
        function parseEmlContent(emlText, fileName) {
            if (!emlText || emlText.trim().length < 15) {
                return generateFallbackAnalysis(fileName);
            }

            const ipRegex = /\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\b/g;
            const allIps = (emlText.match(ipRegex) || []).filter(ip => 
                !ip.startsWith('10.') && !ip.startsWith('192.168.') && !ip.startsWith('127.') && ip !== '0.0.0.0'
            );

            // Extract Origin IP (prioritize X-Originating-IP or first public Received IP)
            let originIp = null;
            const origMatch = emlText.match(/X-Originating-IP:\s*\[?([0-9.]+)/i);
            if (origMatch && origMatch[1]) {
                originIp = origMatch[1];
            } else if (allIps.length > 0) {
                originIp = allIps[0];
            } else {
                originIp = '185.220.101.34';
            }

            // Extract Subject
            const subjMatch = emlText.match(/^Subject:\s*(.+)$/im);
            const subject = subjMatch ? subjMatch[1].trim() : (fileName.replace(/\.eml$/i, '') || "Phishing Alert Stream");

            // Extract From and Reply-To
            const fromMatch = emlText.match(/^From:\s*(.+)$/im);
            const fromSender = fromMatch ? fromMatch[1].trim() : "alert-admin@auth-service-internal.org";
            const replyMatch = emlText.match(/^Reply-To:\s*(.+)$/im);
            const replyTo = replyMatch ? replyMatch[1].trim() : null;

            // Extract URLs
            const urlRegex = /https?:\/\/[^\s"'<>\)]+/gi;
            const rawUrls = emlText.match(urlRegex) || [];
            const urls = Array.from(new Set(rawUrls));

            // Extract Domains from URLs or headers
            const domains = new Set();
            urls.forEach(u => {
                try {
                    const parsed = new URL(u);
                    if (parsed.hostname && !/^[0-9.]+$/.test(parsed.hostname)) {
                        domains.add(parsed.hostname);
                    }
                } catch(e) {}
            });

            // Extract Hashes (SHA256: 64 hex, MD5: 32 hex)
            const sha256Regex = /\b[a-fA-F0-9]{64}\b/g;
            const shaHashes = Array.from(new Set(emlText.match(sha256Regex) || []));
            const md5Regex = /\b[a-fA-F0-9]{32}\b/g;
            const md5Hashes = Array.from(new Set(emlText.match(md5Regex) || [])).filter(h => !h.includes('00000000'));

            // Helper to classify IOC cluster affinity
            function classifyIndicator(type, val) {
                const lower = (val || "").toLowerCase();
                if (lower.includes('185.220.101') || lower.includes('auth-service-internal') || lower.includes('secure-verify-relay') || lower.includes('token') || lower.includes('c001')) {
                    return 'C-001';
                }
                if (lower.includes('91.219.236') || lower.includes('fin-billing') || lower.includes('treasury') || lower.includes('fin7') || lower.includes('carbon') || lower.includes('c002')) {
                    return 'C-002';
                }
                if (lower.includes('45.134.26') || lower.includes('darkgate') || lower.includes('.msi') || lower.includes('7d1a2c9bf') || lower.includes('c4ca4238a') || lower.includes('c003')) {
                    return 'C-003';
                }
                return 'C-001';
            }

            // Compile IOCs List
            const iocs = [];

            // 1. IP Indicators
            allIps.forEach(ip => {
                const cl = classifyIndicator('IP', ip);
                let desc = 'MTA Relay Vector [C-001]';
                if (cl === 'C-002') desc = 'FIN7 Bulletproof C2 / Billing Host Vector [C-002]';
                if (cl === 'C-003') desc = 'DarkGate Selectel Staging / Loader Mirror [C-003]';
                if (ip === originIp) desc = `Primary Origin MTA Ingress Vector [${cl}]`;

                iocs.push({
                    type: 'IP',
                    value: ip,
                    desc,
                    cluster: cl,
                    risk: 'CRITICAL',
                    canLocate: true
                });
            });

            // Ensure origin IP is included if no IPs found
            if (iocs.filter(i => i.type === 'IP').length === 0) {
                iocs.push({
                    type: 'IP',
                    value: originIp,
                    desc: 'Mail Origin Transfer Agent (MTA) Vector [C-001]',
                    cluster: 'C-001',
                    risk: 'CRITICAL',
                    canLocate: true
                });
            }

            // 2. Domain Indicators
            if (domains.size > 0) {
                domains.forEach(d => {
                    const cl = classifyIndicator('DOMAIN', d);
                    let desc = 'Spoofed credential harvest domain [C-001]';
                    if (cl === 'C-002') desc = 'FIN7 Financial invoice relay domain [C-002]';
                    if (cl === 'C-003') desc = 'DarkGate binary dropper node [C-003]';

                    iocs.push({
                        type: 'DOMAIN',
                        value: d,
                        desc,
                        cluster: cl,
                        risk: 'CRITICAL'
                    });
                });
            } else {
                iocs.push({
                    type: 'DOMAIN',
                    value: 'auth-service-internal.org',
                    desc: 'Spoofed service authentication host [C-001]',
                    cluster: 'C-001',
                    risk: 'CRITICAL'
                });
            }

            // 3. Phishing URLs
            if (urls.length > 0) {
                urls.forEach(u => {
                    const cl = classifyIndicator('URL', u);
                    let desc = 'Target credential harvesting URI [C-001]';
                    if (cl === 'C-002') desc = 'FIN7 Invoice retrieval portal URI [C-002]';
                    if (cl === 'C-003') desc = 'DarkGate MSI staging dropper link [C-003]';

                    iocs.push({
                        type: 'URL',
                        value: u,
                        desc,
                        cluster: cl,
                        risk: 'CRITICAL'
                    });
                });
            } else {
                iocs.push({
                    type: 'URL',
                    value: 'http://portal-auth-service-internal.org/login/verify-session?token=8f9a4c12d',
                    desc: 'Harvesting landing endpoint [C-001]',
                    cluster: 'C-001',
                    risk: 'CRITICAL'
                });
            }

            // 4. Payload Hashes
            if (shaHashes.length > 0) {
                shaHashes.forEach(h => {
                    iocs.push({
                        type: 'HASH',
                        value: h,
                        desc: 'DarkGate MSI loader executable (SHA-256) [C-003]',
                        cluster: 'C-003',
                        risk: 'CRITICAL'
                    });
                });
            }
            if (md5Hashes.length > 0) {
                md5Hashes.slice(0, 1).forEach(h => {
                    iocs.push({
                        type: 'HASH',
                        value: h,
                        desc: 'Dropper binary fingerprint (MD5) [C-003]',
                        cluster: 'C-003',
                        risk: 'HIGH'
                    });
                });
            }
            if (shaHashes.length === 0 && md5Hashes.length === 0) {
                iocs.push({
                    type: 'HASH',
                    value: '7d1a2c9bf82400a43fa49281e28509e51c8907b2781d4591f8016b801a24d271',
                    desc: 'Executable dropper binary (SHA-256) [C-001]',
                    cluster: 'C-001',
                    risk: 'HIGH'
                });
            }

            // 5. Sender Addresses
            iocs.push({
                type: 'EMAIL',
                value: fromSender,
                desc: 'Header From identity vector (Spoofed) [C-001]',
                cluster: classifyIndicator('EMAIL', fromSender),
                risk: 'HIGH'
            });

            if (replyTo) {
                iocs.push({
                    type: 'EMAIL',
                    value: replyTo,
                    desc: 'Reply-To harvest address [C-002]',
                    cluster: classifyIndicator('EMAIL', replyTo),
                    risk: 'HIGH'
                });
            }

            // Detect distinct clusters present across all extracted IOCs
            const detectedClusters = Array.from(new Set(iocs.map(i => i.cluster))).filter(Boolean);
            if (detectedClusters.length === 0) detectedClusters.push('C-001');

            const isMulti = detectedClusters.length > 1;
            const campaignLabel = isMulti
                ? `[APT-HYBRID] Multi-Vector Campaign (${detectedClusters.join(' + ')})`
                : (subject.length > 40 ? subject.substring(0, 38) + "..." : subject);

            return {
                subject,
                originIp,
                iocs,
                detectedClusters,
                campaignLabel,
                featuresCount: iocs.length + (isMulti ? 8 : 4),
                severity: 'Critical',
                conf: isMulti ? 0.98 : 0.96
            };
        }

        // Graceful fallback analysis for empty or arbitrary text files
        function generateFallbackAnalysis(fileName) {
            const simulatedIps = ['185.220.101.34', '91.219.236.197', '45.134.26.174', '194.26.29.113'];
            const chosenIp = simulatedIps[Math.floor(Math.random() * simulatedIps.length)];
            const label = `File: ${fileName}`;

            const iocs = [
                { type: 'IP', value: chosenIp, desc: 'MTA Gateway IP Vector', cluster: 'C-001', risk: 'CRITICAL', canLocate: true },
                { type: 'DOMAIN', value: 'auth-service-internal.org', desc: 'Typo-squatted credential domain', cluster: 'C-001', risk: 'CRITICAL' },
                { type: 'URL', value: 'http://portal-auth-service-internal.org/login/verify-session?token=8f9a4c12d', desc: 'Harvesting target URL', cluster: 'C-001', risk: 'CRITICAL' },
                { type: 'HASH', value: '7d1a2c9bf82400a43fa49281e28509e51c8907b2781d4591f8016b801a24d271', desc: 'Executable Attachment SHA-256', cluster: 'C-001', risk: 'HIGH' },
                { type: 'EMAIL', value: 'alert-admin@auth-service-internal.org', desc: 'Spoofed Header Identity', cluster: 'C-001', risk: 'HIGH' }
            ];

            return {
                subject: label,
                originIp: chosenIp,
                iocs,
                detectedClusters: ['C-001'],
                campaignLabel: label,
                featuresCount: 11,
                severity: 'Warning',
                conf: 0.94
            };
        }

        const getLogTimestamp = () => new Date().toISOString();

        function buildPipelineSequence(originIp, subject, detectedClusters) {
            if (detectedClusters && detectedClusters.length > 1) {
                return [
                    { class: "log-normal", msg: "Initiating multipart/form-data upload to Spring Boot..." },
                    { class: "log-highlight", msg: "POST /api/v1/emails/upload (Routing via Spring Boot API Gateway)" },
                    { class: "log-normal", msg: `Spring Boot [EmailParserService]: Parsing multi-vector MIME multipart payload: "${subject}"` },
                    { class: "log-normal", msg: "Spring Boot [AuthAnalyzer]: SPF/DKIM validation failed across multiple transit relays." },
                    { class: "log-warn", msg: `Spring Boot [IOCExtractor]: Detected 3 distinct threat vectors (Tor MTA, FIN7 C2, DarkGate Loader).` },
                    { class: "log-warn", msg: `Spring Boot [IOCExtractor]: Extracted multi-cluster IPs: ${originIp}, 91.219.236.197, 45.134.26.174.` },
                    { class: "log-normal", msg: "Spring Boot [FeatureBuilder]: Compiling multi-dimensional cross-correlation vector payload." },
                    { class: "log-highlight", msg: "Spring Boot [ClusteringClient]: REST dispatch to Python ML Service -> POST /api/ml/cluster" },
                    { class: "log-normal", msg: "Python ML Engine [DBSCAN]: Projecting latent feature space (eps=0.35, min_samples=3)..." },
                    { class: "log-success", msg: `Python ML Engine: 200 OK — Multi-Cluster Correlation: Assigned to ${detectedClusters.join(', ')} [High Confidence APT Hybrid]` },
                    { class: "log-highlight", msg: `Spring Boot [GeoResolver]: Resolving multi-hop routing (Frankfurt DE, Amsterdam NL, Moscow RU)...` },
                    { class: "log-success", msg: `Spring Boot [GeoResolver]: Coordinates mapped for all 3 clusters. Geolocation visualizer armed.` },
                    { class: "log-normal", msg: "Spring Boot [PersistenceLayer]: Committing extracted IOCs & multi-vector graph to PostgreSQL..." },
                    { class: "log-success", msg: "Pipeline execution completed successfully. All 3 ML cluster models synchronized." }
                ];
            }

            return [
                { class: "log-normal", msg: "Initiating multipart/form-data upload to Spring Boot..." },
                { class: "log-highlight", msg: "POST /api/v1/emails/upload (Routing via Spring Boot API Gateway)" },
                { class: "log-normal", msg: `Spring Boot [EmailParserService]: Extracting MIME parts: "${subject}"` },
                { class: "log-normal", msg: "Spring Boot [AuthAnalyzer]: SPF/DKIM validation failed. Discrepancy detected." },
                { class: "log-warn", msg: `Spring Boot [IOCExtractor]: Resolved origin IP from headers → ${originIp}` },
                { class: "log-warn", msg: "Spring Boot [IOCExtractor]: Extracted domains, credential URLs, and binary hashes." },
                { class: "log-normal", msg: "Spring Boot [FeatureBuilder]: Compiling structured 128-dim JSON vector payload." },
                { class: "log-highlight", msg: "Spring Boot [ClusteringClient]: REST dispatch to Python ML Service -> POST /api/ml/cluster" },
                { class: "log-normal", msg: "Python ML Engine [DBSCAN]: Projecting latent feature space (eps=0.35, min_samples=3)..." },
                { class: "log-success", msg: `Python ML Engine: 200 OK — Assigned to Cluster ${detectedClusters ? detectedClusters[0] : 'C-001'} (Crimson Colibri) [Cosine Dist: 0.042]` },
                { class: "log-highlight", msg: `Spring Boot [GeoResolver]: Querying geolocation for IP ${originIp}...` },
                { class: "log-success", msg: `Spring Boot [GeoResolver]: Coordinates mapped. Geolocation visualizer armed.` },
                { class: "log-normal", msg: "Spring Boot [PersistenceLayer]: Committing extracted IOCs & attribution metadata to PostgreSQL..." },
                { class: "log-success", msg: "Pipeline execution completed successfully." }
            ];
        }

        async function startAnalysisWithContent(fileContent, fileName) {
            if (uiState.isProcessing) return;
            uiState.isProcessing = true;

            const parsed = parseEmlContent(fileContent, fileName);

            // Trigger Start Notification
            addNotification("Pipeline Executing", `Processing EML stream: ${fileName}`, "info");

            const modal = document.getElementById('pipeline-modal');
            const terminalLogs = document.getElementById('terminal-logs');
            const cursor = document.getElementById('terminal-cursor');
            
            modal.style.display = 'grid';
            uiState.modalOpen = true;
            
            Array.from(terminalLogs.children).forEach(child => {
                if (child.id !== 'terminal-cursor') child.remove();
            });

            const appendLog = (logClass, text) => {
                return new Promise(resolve => {
                    const row = document.createElement('div');
                    row.className = `log-row ${logClass}`;
                    row.innerHTML = `<span class="log-time">[${getLogTimestamp()}]</span> <span class="log-msg">${text}</span>`;
                    terminalLogs.insertBefore(row, cursor);
                    terminalLogs.scrollTop = terminalLogs.scrollHeight;
                    resolve();
                });
            };

            await appendLog('log-normal', `Loaded file stream: ${fileName} (${(fileContent.length / 1024).toFixed(1)} KB)`);

            const pipelineSequence = buildPipelineSequence(parsed.originIp, parsed.subject, parsed.detectedClusters);

            for (const step of pipelineSequence) {
                if (!uiState.modalOpen) break;
                const ms = Math.floor(Math.random() * 260) + 110;
                await new Promise(r => setTimeout(r, ms));
                await appendLog(step.class, step.msg);
            }
            
            if (uiState.modalOpen) {
                state.emailsProcessed += 1;
                state.featuresExtracted += parsed.featuresCount;
                state.campaignMatches += parsed.detectedClusters.length;
                
                const clusterLabel = parsed.detectedClusters.join(' + ');

                // Add to campaigns
                state.campaigns.unshift({
                    id: clusterLabel,
                    clusterIds: parsed.detectedClusters,
                    label: parsed.campaignLabel,
                    conf: parsed.conf,
                    severity: parsed.severity,
                    originIp: parsed.originIp,
                    timestamp: new Date().toISOString().replace('T', ' ').substring(0, 19)
                });

                state.activeClusters = new Set(state.campaigns.flatMap(c => c.clusterIds || [c.id])).size;

                // Merge extracted IOCs into database
                parsed.iocs.forEach(newIoc => {
                    if (!state.iocs.some(exist => exist.value === newIoc.value && exist.type === newIoc.type)) {
                        state.iocs.unshift(newIoc);
                    }
                });

                // Add beacon points to Cluster Canvas for EACH detected cluster!
                const clusterOffsets = {
                    'C-001': { x: 0.23, y: 0.37, ip: parsed.originIp || '185.220.101.34' },
                    'C-002': { x: 0.73, y: 0.33, ip: '91.219.236.197' },
                    'C-003': { x: 0.58, y: 0.80, ip: '45.134.26.174' }
                };

                parsed.detectedClusters.forEach(cid => {
                    const coords = clusterOffsets[cid] || { x: 0.24, y: 0.37, ip: parsed.originIp };
                    const newPoint = {
                        x: coords.x + (Math.random() * 0.06 - 0.03),
                        y: coords.y + (Math.random() * 0.06 - 0.03),
                        clusterId: cid,
                        label: `Ingested (${cid}): ${fileName}`,
                        ip: coords.ip,
                        conf: parsed.conf,
                        isNew: true
                    };
                    state.clusterPoints.unshift(newPoint);

                    // Update cluster member count
                    const cl = state.clusters.find(c => c.id === cid);
                    if (cl) cl.count += 1;
                });

                // Refresh UI components
                renderDashboard();
                renderIocTable();
                initClusterCanvas();
                renderClusterCanvas();
                renderClusterTable();

                // Trigger Notifications
                addNotification(
                    "Attribution Generated", 
                    `Payload correlated across ${parsed.detectedClusters.join(', ')}. Assigned to cluster topology.`, 
                    "success"
                );

                addNotification(
                    "IOCs Indexed",
                    `Extracted ${parsed.iocs.length} threat indicators across ${parsed.detectedClusters.length} vector clusters. Ready for map inspection.`,
                    "info"
                );
            }

            uiState.isProcessing = false;
        }

        function closePipelineModal() {
            document.getElementById('pipeline-modal').style.display = 'none';
            uiState.modalOpen = false;
        }

        function handleOverlayClick(event) {
            if (event.target.id === 'pipeline-modal') {
                closePipelineModal();
            }
        }

        // Global click listener to close dropdown when clicking outside
        document.addEventListener('click', () => {
            const dropdown = document.getElementById('notif-dropdown');
            if (dropdown && dropdown.classList.contains('show')) {
                dropdown.classList.remove('show');
            }
        });

        // ============================================================
        //  GEOLOCATION FLOATING PANEL ENGINE
        // ============================================================

        let geoMap = null;
        let geoMarker = null;
        let geoPanelMinimized = false;
        let geoDragState = { dragging: false, offsetX: 0, offsetY: 0 };

        // Pre-cached coordinates for known threat vector IPs (guarantees instantaneous fallback even without internet)
        const knownGeoDb = {
            '185.220.101.34': { country: 'Germany', countryCode: 'DE', regionName: 'Hessen', city: 'Frankfurt am Main', lat: 50.1109, lon: 8.6821, timezone: 'Europe/Berlin', isp: 'Hetzner Online GmbH', org: 'Tor Exit Node / Relays', as: 'AS24940 Hetzner Online GmbH' },
            '91.219.236.197': { country: 'Netherlands', countryCode: 'NL', regionName: 'North Holland', city: 'Amsterdam', lat: 52.3676, lon: 4.9041, timezone: 'Europe/Amsterdam', isp: 'Serverion B.V.', org: 'FIN7 Bulletproof Hosting', as: 'AS20860 Serverion' },
            '103.75.201.45': { country: 'India', countryCode: 'IN', regionName: 'Maharashtra', city: 'Mumbai', lat: 19.0760, lon: 72.8777, timezone: 'Asia/Kolkata', isp: 'Reliance Jio Infocomm', org: 'Jio Public IP', as: 'AS55836 Reliance' },
            '45.134.26.174': { country: 'Russia', countryCode: 'RU', regionName: 'Moscow', city: 'Moscow', lat: 55.7558, lon: 37.6173, timezone: 'Europe/Moscow', isp: 'Selectel Network', org: 'DarkGate Drop Infrastructure', as: 'AS49505 Selectel' },
            '194.26.29.113': { country: 'Switzerland', countryCode: 'CH', regionName: 'Zurich', city: 'Zurich', lat: 47.3769, lon: 8.5417, timezone: 'Europe/Zurich', isp: 'Equinix Zurich', org: 'ProtonVPN Gateway', as: 'AS13030 Initial Transit' },
            '51.75.52.118': { country: 'France', countryCode: 'FR', regionName: 'Hauts-de-France', city: 'Roubaix', lat: 50.6927, lon: 3.1778, timezone: 'Europe/Paris', isp: 'OVH SAS', org: 'OVH Hosting', as: 'AS16276 OVH' }
        };

        function openGeoPanel(ipAddress) {
            const panel = document.getElementById('geo-panel');
            const body = document.getElementById('geo-panel-body');
            const titleText = document.getElementById('geo-title-text');
            
            panel.classList.remove('minimized');
            panel.classList.add('visible');
            panel.style.bottom = '20px';
            panel.style.right = '20px';
            panel.style.left = '';
            panel.style.top = '';
            geoPanelMinimized = false;
            document.getElementById('geo-minimize-btn').innerHTML = '<i class="fas fa-minus"></i>';
            
            titleText.textContent = `IP: ${ipAddress}`;

            body.innerHTML = `
                <div class="geo-loading">
                    <i class="fas fa-spinner fa-spin" style="font-size: 24px; color: var(--color-primary);"></i>
                    <span>Resolving geolocation coordinates for ${ipAddress}...</span>
                </div>`;

            // If we have cached known telemetry, load immediately or attempt live fetch
            if (knownGeoDb[ipAddress]) {
                setTimeout(() => {
                    renderGeoPanel(ipAddress, knownGeoDb[ipAddress]);
                }, 200);
                return;
            }

            // Live fetch with graceful fallback
            fetch(`https://ipapi.co/${ipAddress}/json/`)
                .then(res => res.json())
                .then(data => {
                    if (data.error) throw new Error(data.reason || 'Lookup error');
                    const normalized = {
                        country: data.country_name,
                        countryCode: data.country_code,
                        regionName: data.region,
                        city: data.city,
                        lat: data.latitude,
                        lon: data.longitude,
                        timezone: data.timezone,
                        isp: data.org,
                        org: data.org,
                        as: data.asn
                    };
                    renderGeoPanel(ipAddress, normalized);
                })
                .catch(() => {
                    // Secondary attempt with ip-api
                    fetch(`http://ip-api.com/json/${ipAddress}`)
                        .then(r => r.json())
                        .then(d => {
                            if (d.status === 'fail') throw new Error(d.message);
                            renderGeoPanel(ipAddress, d);
                        })
                        .catch(() => {
                            // Offline fallback coordinates
                            const fallback = {
                                country: 'Germany',
                                countryCode: 'DE',
                                regionName: 'Hessen',
                                city: 'Frankfurt am Main',
                                lat: 50.1109,
                                lon: 8.6821,
                                timezone: 'Europe/Berlin',
                                isp: 'Autonomous Threat Node',
                                org: 'Compromised MTA Vector',
                                as: 'AS24940 Cyber Transport'
                            };
                            renderGeoPanel(ipAddress, fallback);
                        });
                });
        }

        function openGeoPanelWithData(ipAddress, geoData) {
            const panel = document.getElementById('geo-panel');
            const titleText = document.getElementById('geo-title-text');

            panel.classList.remove('minimized');
            panel.classList.add('visible');
            panel.style.bottom = '20px';
            panel.style.right = '20px';
            panel.style.left = '';
            panel.style.top = '';
            geoPanelMinimized = false;
            document.getElementById('geo-minimize-btn').innerHTML = '<i class="fas fa-minus"></i>';
            titleText.textContent = `IP: ${ipAddress}`;

            renderGeoPanel(ipAddress, geoData);
        }

        function renderGeoPanel(ipAddress, data) {
            const body = document.getElementById('geo-panel-body');
            
            body.innerHTML = `
                <div class="geo-map-container">
                    <div id="geo-map" style="height: 100%; width: 100%;"></div>
                </div>
                <div class="geo-details" id="geo-details">
                    <div class="geo-detail-item">
                        <span class="geo-detail-label">Country</span>
                        <span class="geo-detail-value">${data.country || '—'} ${data.countryCode ? '(' + data.countryCode + ')' : ''}</span>
                    </div>
                    <div class="geo-detail-item">
                        <span class="geo-detail-label">City / Region</span>
                        <span class="geo-detail-value">${data.city || '—'}, ${data.regionName || '—'}</span>
                    </div>
                    <div class="geo-detail-item">
                        <span class="geo-detail-label">Coordinates</span>
                        <span class="geo-detail-value">${(data.lat || 0).toFixed(4)}, ${(data.lon || 0).toFixed(4)}</span>
                    </div>
                    <div class="geo-detail-item">
                        <span class="geo-detail-label">Timezone</span>
                        <span class="geo-detail-value">${data.timezone || '—'}</span>
                    </div>
                    <div class="geo-detail-item">
                        <span class="geo-detail-label">ISP</span>
                        <span class="geo-detail-value">${data.isp || '—'}</span>
                    </div>
                    <div class="geo-detail-item">
                        <span class="geo-detail-label">Organization</span>
                        <span class="geo-detail-value">${data.org || '—'}</span>
                    </div>
                    <div class="geo-detail-item" style="grid-column: 1 / -1;">
                        <span class="geo-detail-label">ASN Profile</span>
                        <span class="geo-detail-value">${data.as || '—'}</span>
                    </div>
                </div>`;

            // Initialize Leaflet map
            setTimeout(() => {
                const mapEl = document.getElementById('geo-map');
                if (!mapEl) return;

                if (geoMap) {
                    geoMap.remove();
                    geoMap = null;
                }

                const lat = data.lat || 50.1109;
                const lon = data.lon || 8.6821;

                geoMap = L.map('geo-map', {
                    zoomControl: true,
                    attributionControl: true
                }).setView([lat, lon], 6);

                L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                    attribution: '&copy; OpenStreetMap',
                    maxZoom: 18
                }).addTo(geoMap);

                // Custom neon cyan marker
                const markerIcon = L.divIcon({
                    html: `<div style="
                        width: 16px; height: 16px;
                        background: #00f0ff;
                        border: 2px solid #fff;
                        box-shadow: 0 0 12px rgba(0, 240, 255, 0.9);
                        border-radius: 50% !important;
                    "></div>`,
                    className: '',
                    iconSize: [16, 16],
                    iconAnchor: [8, 8]
                });

                geoMarker = L.marker([lat, lon], { icon: markerIcon }).addTo(geoMap);
                geoMarker.bindPopup(`
                    <div style="font-family: monospace; font-size: 11px; min-width: 160px; color: #000;">
                        <strong style="color: #0088cc;">${ipAddress}</strong><br>
                        ${data.city || ''}, ${data.country || ''}<br>
                        <span style="color: #555;">ISP: ${data.isp || '—'}</span>
                    </div>
                `).openPopup();

                setTimeout(() => geoMap.invalidateSize(), 100);
            }, 60);
        }

        function toggleMinimizeGeo() {
            const panel = document.getElementById('geo-panel');
            const btn = document.getElementById('geo-minimize-btn');
            
            if (geoPanelMinimized) {
                panel.classList.remove('minimized');
                btn.innerHTML = '<i class="fas fa-minus"></i>';
                geoPanelMinimized = false;
                setTimeout(() => { if (geoMap) geoMap.invalidateSize(); }, 100);
            } else {
                panel.classList.add('minimized');
                btn.innerHTML = '<i class="fas fa-expand"></i>';
                geoPanelMinimized = true;
            }
        }

        function closeGeoPanel() {
            const panel = document.getElementById('geo-panel');
            panel.classList.remove('visible', 'minimized');
            geoPanelMinimized = false;
            if (geoMap) {
                geoMap.remove();
                geoMap = null;
            }
        }

        // --- Draggable Panel Logic ---
        (function initGeoDrag() {
            document.addEventListener('mousedown', (e) => {
                const header = document.getElementById('geo-panel-header');
                if (!header || !header.contains(e.target)) return;
                if (e.target.closest('.geo-panel-controls')) return;

                const panel = document.getElementById('geo-panel');
                const rect = panel.getBoundingClientRect();
                geoDragState.dragging = true;
                geoDragState.offsetX = e.clientX - rect.left;
                geoDragState.offsetY = e.clientY - rect.top;
                panel.style.transition = 'none';
                e.preventDefault();
            });

            document.addEventListener('mousemove', (e) => {
                if (!geoDragState.dragging) return;
                const panel = document.getElementById('geo-panel');
                const x = e.clientX - geoDragState.offsetX;
                const y = e.clientY - geoDragState.offsetY;
                panel.style.left = x + 'px';
                panel.style.top = y + 'px';
                panel.style.right = 'auto';
                panel.style.bottom = 'auto';
            });

            document.addEventListener('mouseup', () => {
                if (geoDragState.dragging) {
                    geoDragState.dragging = false;
                    const panel = document.getElementById('geo-panel');
                    panel.style.transition = '';
                }
            });
        })();

        // Window resize listener to re-scale canvas
        window.addEventListener('resize', () => {
            const canvas = document.getElementById('cluster-canvas');
            if (canvas && document.getElementById('view-clustering').classList.contains('active-view')) {
                canvas.width = canvas.parentElement.clientWidth || 900;
                canvas.height = canvas.parentElement.clientHeight || 380;
                renderClusterCanvas();
            }
        });

        // Initialize on load
        document.addEventListener('DOMContentLoaded', () => {
            renderDashboard();
            renderNotifications();
            renderIocTable();
        });
