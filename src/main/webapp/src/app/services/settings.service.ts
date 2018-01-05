import { Settings } from './../models/settings';
import { Observable } from 'rxjs/Observable';
import { Injectable } from '@angular/core';
import { ApiService } from './api.service';
import { Response } from '@angular/http';
import { Language } from '../models/language';

@Injectable()
export class SettingsService {

  languages: Language[] = [];
  settings: Settings;
  observable: Observable<Settings>;

  constructor(private apiService: ApiService) {
    let language: Language;

    language = new Language();
    language.key = 'en';
    language.name = 'English';
    this.languages.push(language);

    language = new Language();
    language.key = 'de';
    language.name = 'Deutsch';
    this.languages.push(language);
  }

  getSettings(clearCache: boolean = false): Observable<Settings> {
    if(clearCache) {
      this.settings = undefined;
      this.observable = undefined;
    }

    if (this.settings) {
      return Observable.of(this.settings);
    }

    if(this.observable) {
      return this.observable;
    }

    this.observable = this.apiService.get('system/settings')
    .map((response: Response) => {
      this.settings = new Settings(response.json());
      this.observable = undefined;

      return this.settings;
    });

    return this.observable;
  }

}
