import { useEffect, useState } from 'react';
import './App.css';
import sampleMetrics from './data/sampleMetrics';

const severityDotColor = {
  critical: '#f87171',
  high: '#fb923c',
  medium: '#facc15',
  low: '#34d399',
};

function circularGradient(percent) {
  const angle = (percent / 100) * 360;
  return `conic-gradient(#22d3ee ${angle}deg, rgba(148, 163, 184, 0.2) ${angle}deg)`;
}

function StatCard({ label, value, detail, children }) {
  return (
    <div className="card">
      <div className="label">{label}</div>
      <div className="value">{value}</div>
      <div className="detail">{detail}</div>
      {children}
    </div>
  );
}

function App() {
  const [metrics, setMetrics] = useState(sampleMetrics);

  useEffect(() => {
    fetch('metrics.json', { cache: 'no-store' })
      .then((res) => (res.ok ? res.json() : Promise.reject()))
      .then((remote) => {
        setMetrics((prev) => ({
          ...prev,
          ...remote,
          tests: { ...prev.tests, ...remote.tests },
          coverage: { ...prev.coverage, ...remote.coverage },
          mutation: { ...prev.mutation, ...remote.mutation },
          dependencyCheck: { ...prev.dependencyCheck, ...remote.dependencyCheck },
          timeline: remote.timeline && remote.timeline.length ? remote.timeline : prev.timeline,
          console: remote.console && remote.console.length ? remote.console : prev.console,
          run: { ...prev.run, ...remote.run },
        }));
      })
      .catch(() => {
        // keep sample data if metrics.json is missing (local dev)
      });
  }, []);

  const tests = metrics.tests;
  const coverage = metrics.coverage;
  const mutation = metrics.mutation;
  const dep = metrics.dependencyCheck;
  const timeline = metrics.timeline && metrics.timeline.length ? metrics.timeline : sampleMetrics.timeline;
  const consoleLines = metrics.console && metrics.console.length ? metrics.console : sampleMetrics.console;

  return (
    <div className="app">
      <header className="header">
        <div>
          <h1>contact-service-junit · QA Console</h1>
          <small>
            {metrics.run.workflow} &mdash; {metrics.run.os} / JDK {metrics.run.jdk} ·{' '}
            {metrics.run.timestamp}
          </small>
        </div>
        <div className="badge-row">
          <span className="badge">{metrics.run.branch}</span>
          <span className="badge">#{metrics.run.commit}</span>
          <span className="badge">{metrics.run.author}</span>
        </div>
      </header>

      <section className="grid">
        <StatCard
          label="Tests"
          value={`${tests.passed}/${tests.total} passed`}
          detail={`Failures: ${tests.failed}, Errors: ${tests.errors}, Skipped: ${tests.skipped}, Runtime: ${tests.duration}s`}
        >
          <div className="progress">
            <div
              className="progress-bar"
              style={{ width: `${(tests.passed / tests.total) * 100}%` }}
            />
          </div>
        </StatCard>

        <div className="card coverage">
          <div className="label">Coverage</div>
          <div className="coverage-ring">
            <div
              className="ring"
              style={{ background: circularGradient(coverage.percent) }}
            >
              <span>{coverage.percent}%</span>
            </div>
          </div>
          <div className="detail">{`${coverage.covered} / ${coverage.total} lines`}</div>
        </div>

        <StatCard
          label="Mutation Score"
          value={`${mutation.percent}%`}
          detail={`Killed ${mutation.killed}, Survived ${mutation.survived}, No coverage ${mutation.noCoverage}`}
        >
          <div className="progress">
            <div
              className="progress-bar"
              style={{ width: `${mutation.percent}%` }}
            />
          </div>
        </StatCard>

        <StatCard
          label="Dependency-Check"
          value={`${dep.vulnerableDeps}/${dep.scanned} deps`}
          detail={`${Object.values(dep.vulnerabilities).reduce(
            (sum, count) => sum + count,
            0
          )} total vulnerabilities`}
        >
          <ul className="severity-list">
            {Object.entries(dep.vulnerabilities).map(([level, count]) => (
              <li key={level}>
                <span
                  className="severity-dot"
                  style={{ background: severityDotColor[level] || '#94a3b8' }}
                />
                {level.toUpperCase()}: {count}
              </li>
            ))}
          </ul>
        </StatCard>
      </section>

      <section className="timeline">
        {timeline.map((stage) => (
          <div key={stage.stage} className={`stage ${stage.status}`}>
            <div className="orb">{stage.short || stage.stage.charAt(0)}</div>
            <div className="name">{stage.stage}</div>
            <div className="duration">{stage.duration}s</div>
          </div>
        ))}
      </section>

      <section className="console">
        {consoleLines.map((line, index) => {
          const [tag, message] = line.split('] ');
          const cleanTag = tag.replace('[', '').toUpperCase();
          return (
            <div key={index} className="console-line">
              <span className="console-tag">{cleanTag}]</span>
              <span className="console-msg">{message}</span>
            </div>
          );
        })}
      </section>

      <div className="links">
        <a className="link-button" href="../target/site/jacoco/index.html">
          JaCoCo Report
        </a>
        <a className="link-button" href="../target/site/spotbugs.html">
          SpotBugs Report
        </a>
        <a className="link-button" href="../target/dependency-check-report.html">
          Dependency-Check
        </a>
        <a className="link-button" href="../target/pit-reports/index.html">
          PITest Report
        </a>
      </div>
    </div>
  );
}

export default App;
