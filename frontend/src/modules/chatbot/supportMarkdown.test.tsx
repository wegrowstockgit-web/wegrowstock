import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { SupportMarkdown } from './supportMarkdown';

describe('SupportMarkdown', () => {
  it('highlights instructor sections and numbered action steps', () => {
    render(
      <SupportMarkdown
        text={[
          '**🔍 Diagnosis**',
          'Orders are stuck on credit hold.',
          '',
          '**✅ Action Plan**',
          '1. Open **Sales Orders**.',
          '2. Tap **Release hold**.',
          '',
          '**👥 Downstream Impact**',
          'Pickers can resume allocation.',
        ].join('\n')}
      />,
    );

    const sections = screen.getAllByTestId('support-markdown-section');
    expect(sections.map((el) => el.textContent)).toEqual(
      expect.arrayContaining([
        expect.stringMatching(/Diagnosis/i),
        expect.stringMatching(/Action Plan/i),
        expect.stringMatching(/Downstream Impact/i),
      ]),
    );
    expect(screen.getByTestId('support-markdown')).toHaveTextContent(/Release hold/);
  });
});
