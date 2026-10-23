import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PublicChat } from './public-chat';

describe('PublicChat', () => {
  let component: PublicChat;
  let fixture: ComponentFixture<PublicChat>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PublicChat],
    }).compileComponents();

    fixture = TestBed.createComponent(PublicChat);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
