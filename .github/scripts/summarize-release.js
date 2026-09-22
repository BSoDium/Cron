const MODEL = process.env.RELEASE_SUMMARY_MODEL || 'openai/gpt-4o-mini';
const ENDPOINT = 'https://models.github.ai/inference/chat/completions';

const SYSTEM_PROMPT = `You write release notes for Cron, an Android alarm app.
You'll be given a raw "What's Changed" list of merged PR titles (often using
conventional-commit prefixes like feat/fix/chore/refactor/style).
Rewrite it as a short summary for end users, not developers:
- 3-6 bullet points grouped by what a user would notice (new features, fixes, other).
- Plain language: drop prefixes, PR numbers, usernames, and internal jargon.
- Skip purely internal changes (ci, chore, refactor, style, test) unless nothing else remains.
- No preamble, no closing remarks, just the bullets in Markdown.`;

module.exports = async ({ github, context, core }) => {
  const tag = process.env.TAG;
  const token = process.env.GITHUB_TOKEN;

  const { data: release } = await github.rest.repos.getReleaseByTag({
    owner: context.repo.owner,
    repo: context.repo.repo,
    tag,
  });

  if (!release.body || !release.body.trim()) {
    core.info('Release has no body to summarize, skipping.');
    return;
  }

  let summary;
  try {
    const response = await fetch(ENDPOINT, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        model: MODEL,
        temperature: 0.3,
        messages: [
          { role: 'system', content: SYSTEM_PROMPT },
          { role: 'user', content: release.body },
        ],
      }),
    });

    if (!response.ok) {
      throw new Error(`${response.status} ${response.statusText}: ${await response.text()}`);
    }

    const payload = await response.json();
    summary = payload.choices?.[0]?.message?.content?.trim();
    if (!summary) {
      throw new Error('Empty completion from model.');
    }
  } catch (error) {
    core.warning(`Skipping AI release summary, leaving auto-generated notes as-is: ${error}`);
    return;
  }

  const newBody = `${summary}\n\n<details>\n<summary>Full changelog</summary>\n\n${release.body}\n\n</details>`;

  await github.rest.repos.updateRelease({
    owner: context.repo.owner,
    repo: context.repo.repo,
    release_id: release.id,
    body: newBody,
  });

  core.info(`Updated release ${tag} with an AI-generated summary (model: ${MODEL}).`);
};
