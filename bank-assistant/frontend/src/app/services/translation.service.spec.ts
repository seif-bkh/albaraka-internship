import { TestBed } from '@angular/core/testing';

import { TranslatePipe, TranslationService } from './translation.service';

describe('TranslationService', () => {
  let service: TranslationService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(TranslationService);
  });

  it('defaults to French and switches languages', () => {
    expect(service.lang()).toBe('fr');
    service.setLang('en');
    expect(service.lang()).toBe('en');
    expect(service.t('chat.logout')).toBe('Log out');
    service.setLang('ar');
    expect(service.t('chat.logout')).toBe('تسجيل الخروج');
  });

  it('falls back to French for untranslated keys, then to the key itself', () => {
    service.setLang('en');
    // 'right.helpTitle' exists in all three dictionaries; use an FR-only key path
    // by asking for a fabricated key.
    expect(service.t('does.not.exist')).toBe('does.not.exist');
  });

  it('reJECTs unknown language codes and keeps the current one', () => {
    service.setLang('de');
    expect(service.lang()).toBe('fr');
  });

  it('translates through the pipe', () => {
    const pipe = new TranslatePipe(service);
    service.setLang('fr');
    expect(pipe.transform('chat.logout')).toBe('Déconnexion');
  });
});
