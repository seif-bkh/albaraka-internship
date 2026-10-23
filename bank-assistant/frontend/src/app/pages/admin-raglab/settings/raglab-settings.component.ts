import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../../services/auth.service';
import {
  RaglabService,
  RaglabProfile,
  RaglabModelsResponse,
  RaglabKeyItem,
  RaglabSanityResponse,
  parseRaglabError,
  ParsedRaglabError
} from '../../../services/raglab.service';

type SettingsTab = 'models' | 'retrieval' | 'keys';

@Component({
  selector: 'app-raglab-settings',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './raglab-settings.component.html',
  styleUrl: './raglab-settings.component.css'
})
export class RaglabSettingsComponent implements OnInit {
  activeTab: SettingsTab = 'models';

  // --- 1. Models Tab
  /** Providers and models are stable lists; tracking by name keeps the select focus. */
  trackProvider = (_: number, p: { provider: string }): string => p.provider;
  trackModel = (_: number, m: string): string => m;

  modelsData: RaglabModelsResponse | null = null;
  loadingModels = false;
  modelsError: ParsedRaglabError | null = null;

  selectedEmbeddingProvider = '';
  selectedEmbeddingModel = '';
  selectedAnswerProvider = '';
  selectedAnswerModel = '';

  customModels: Record<string, string> = {}; // provider -> custom typed id
  customModelInput: Record<string, string> = {};

  isSwitchingProfile = false;
  profileSwitchNotes: string[] = [];
  collectionChangedWarning = false;
  newCollectionName = '';

  // --- 2. Retrieval & Corpus Tab
  profileForm: Partial<RaglabProfile> = {
    chunking: { mode: 'restructure', size: 220, overlap: 40 },
    retrieval: { top_k: 5, mode: 'vector', lang_filter: null, neighbor_radius: 0 },
    data_dirs: []
  };
  newDataDir = '';
  isSavingRetrieval = false;
  retrievalSaveSuccess: string | null = null;
  retrievalSaveError: ParsedRaglabError | null = null;

  // --- 3. API Keys Tab
  keysList: RaglabKeyItem[] = [];
  loadingKeys = false;
  keysError: ParsedRaglabError | null = null;

  selectedKeyEnv = '';
  keyValue = '';
  showKeyValue = false;
  keyPersist = false;
  isSavingKey = false;
  keySaveFeedback: { ok: boolean; msg: string } | null = null;

  // Delete key modal
  deletingKeyEnv: string | null = null;
  isDeletingKey = false;

  // Sanity test
  sanityResult: RaglabSanityResponse | null = null;
  sanityLoading = false;
  sanityError: ParsedRaglabError | null = null;

  constructor(public raglab: RaglabService, public auth: AuthService) {}

  get canManage(): boolean { return this.auth.hasPermission('RAGLAB_CONSOLE_MANAGE'); }
  get canRun(): boolean { return this.auth.hasPermission('RAGLAB_CONSOLE_RUN'); }

  ngOnInit(): void {
    this.loadCustomModelsFromStorage();
    this.loadModelsAndProfile();
    this.loadKeys();
  }

  setTab(tab: SettingsTab): void {
    this.activeTab = tab;
    if (tab === 'keys' && this.keysList.length === 0) {
      this.loadKeys();
    }
  }

  private loadCustomModelsFromStorage(): void {
    try {
      const saved = localStorage.getItem('raglab_custom_models');
      if (saved) {
        this.customModels = JSON.parse(saved);
      }
    } catch {
      this.customModels = {};
    }
  }

  private saveCustomModelsToStorage(): void {
    try {
      localStorage.setItem('raglab_custom_models', JSON.stringify(this.customModels));
    } catch {
      // ignore
    }
  }

  loadModelsAndProfile(): void {
    this.loadingModels = true;
    this.modelsError = null;

    this.raglab.getModels().subscribe({
      next: (m) => {
        this.modelsData = m;
        this.raglab.getProfile().subscribe({
          next: (p) => {
            this.loadingModels = false;
            this.syncFromProfile(p.profile);
          },
          error: (err) => {
            this.loadingModels = false;
            this.modelsError = parseRaglabError(err);
          }
        });
      },
      error: (err) => {
        this.loadingModels = false;
        this.modelsError = parseRaglabError(err);
      }
    });
  }

  private syncFromProfile(p: RaglabProfile): void {
    if (!p) return;
    this.selectedEmbeddingProvider = p.embedding?.provider || '';
    this.selectedEmbeddingModel = p.embedding?.model || '';
    this.selectedAnswerProvider = p.answer?.provider || '';
    this.selectedAnswerModel = p.answer?.model || '';

    this.profileForm = {
      chunking: {
        mode: p.chunking?.mode || 'restructure',
        size: p.chunking?.size || 220,
        overlap: p.chunking?.overlap || 40
      },
      retrieval: {
        top_k: p.retrieval?.top_k || 5,
        mode: p.retrieval?.mode || 'vector',
        lang_filter: p.retrieval?.lang_filter ?? null,
        neighbor_radius: p.retrieval?.neighbor_radius ?? 0
      },
      data_dirs: [...(p.data_dirs || [])]
    };
  }

  selectModel(slot: 'embedding' | 'answer', provider: string, model: string): void {
    if (!this.canManage) return;
    if (slot === 'embedding') {
      this.selectedEmbeddingProvider = provider;
      this.selectedEmbeddingModel = model;
    } else {
      this.selectedAnswerProvider = provider;
      this.selectedAnswerModel = model;
    }
  }

  applyCustomModel(slot: 'embedding' | 'answer', provider: string): void {
    if (!this.canManage) return;
    const input = (this.customModelInput[provider] || '').trim();
    if (!input || input.includes(' ')) {
      return;
    }
    this.customModels[provider] = input;
    this.saveCustomModelsToStorage();
    this.selectModel(slot, provider, input);
  }

  switchSlot(slot: 'embedding' | 'answer'): void {
    if (!this.canManage || this.raglab.isSealed()) return;
    this.isSwitchingProfile = true;
    this.profileSwitchNotes = [];
    this.collectionChangedWarning = false;

    const payload: Partial<RaglabProfile> = {};
    if (slot === 'embedding') {
      payload.embedding = {
        provider: this.selectedEmbeddingProvider,
        model: this.selectedEmbeddingModel
      };
    } else {
      payload.answer = {
        provider: this.selectedAnswerProvider,
        model: this.selectedAnswerModel
      };
    }

    const currentColl = this.raglab.health()?.index?.collection;

    this.raglab.updateProfile(payload).subscribe({
      next: (res) => {
        this.isSwitchingProfile = false;
        this.profileSwitchNotes = res.notes || [];
        if (res.collection && currentColl && res.collection !== currentColl) {
          this.collectionChangedWarning = true;
          this.newCollectionName = res.collection;
        }
      },
      error: (err) => {
        this.isSwitchingProfile = false;
        this.modelsError = parseRaglabError(err);
      }
    });
  }

  // --- Retrieval & Corpus actions
  saveRetrievalSettings(): void {
    if (!this.canManage || this.raglab.isSealed()) return;
    this.isSavingRetrieval = true;
    this.retrievalSaveSuccess = null;
    this.retrievalSaveError = null;

    const payload: Partial<RaglabProfile> = {
      chunking: this.profileForm.chunking,
      retrieval: this.profileForm.retrieval,
      data_dirs: this.profileForm.data_dirs
    };

    this.raglab.updateProfile(payload).subscribe({
      next: (res) => {
        this.isSavingRetrieval = false;
        this.retrievalSaveSuccess = `Paramètres enregistrés avec succès. Collection active : ${res.collection}`;
      },
      error: (err) => {
        this.isSavingRetrieval = false;
        this.retrievalSaveError = parseRaglabError(err);
      }
    });
  }

  addDataDir(): void {
    if (!this.canManage) return;
    const d = this.newDataDir.trim();
    if (d && !this.profileForm.data_dirs?.includes(d)) {
      this.profileForm.data_dirs = [...(this.profileForm.data_dirs || []), d];
      this.newDataDir = '';
    }
  }

  removeDataDir(index: number): void {
    if (!this.canManage) return;
    if (this.profileForm.data_dirs) {
      this.profileForm.data_dirs.splice(index, 1);
    }
  }

  // --- Keys actions
  loadKeys(): void {
    this.loadingKeys = true;
    this.keysError = null;
    this.raglab.getKeys().subscribe({
      next: (res) => {
        this.loadingKeys = false;
        this.keysList = res.keys || [];
        if (!this.selectedKeyEnv && this.keysList.length > 0) {
          this.selectedKeyEnv = this.keysList[0].env;
        }
      },
      error: (err) => {
        this.loadingKeys = false;
        this.keysError = parseRaglabError(err);
      }
    });
  }

  onSaveKey(): void {
    if (!this.canManage) return;
    const env = this.selectedKeyEnv.trim();
    const val = this.keyValue.trim();
    if (!env || !val) return;

    this.isSavingKey = true;
    this.keySaveFeedback = null;

    this.raglab.setKey(env, val, this.keyPersist).subscribe({
      next: () => {
        this.isSavingKey = false;
        this.keyValue = '';
        this.keySaveFeedback = { ok: true, msg: `Clé ${env} mise à jour avec succès.` };
        this.loadKeys();
      },
      error: (err) => {
        this.isSavingKey = false;
        const parsed = parseRaglabError(err);
        this.keySaveFeedback = { ok: false, msg: parsed.message };
      }
    });
  }

  promptDeleteKey(env: string): void {
    if (!this.canManage) return;
    this.deletingKeyEnv = env;
  }

  confirmDeleteKey(): void {
    if (!this.canManage || !this.deletingKeyEnv) return;
    this.isDeletingKey = true;

    this.raglab.deleteKey(this.deletingKeyEnv, true).subscribe({
      next: () => {
        this.isDeletingKey = false;
        this.deletingKeyEnv = null;
        this.loadKeys();
      },
      error: (err) => {
        this.isDeletingKey = false;
        this.keysError = parseRaglabError(err);
      }
    });
  }

  cancelDeleteKey(): void {
    this.deletingKeyEnv = null;
  }

  runSanityTest(): void {
    if (!this.canRun) return;
    this.sanityLoading = true;
    this.sanityError = null;
    this.sanityResult = null;

    this.raglab.embeddingsSanity().subscribe({
      next: (res) => {
        this.sanityLoading = false;
        this.sanityResult = res;
      },
      error: (err) => {
        this.sanityLoading = false;
        this.sanityError = parseRaglabError(err);
      }
    });
  }
}
