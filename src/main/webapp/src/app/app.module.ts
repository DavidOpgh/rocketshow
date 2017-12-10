import { Song } from './models/song';
import { State } from './models/state';
import { BrowserModule } from '@angular/platform-browser';
import { NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

import { RouterModule, Routes } from '@angular/router';

import { HttpModule } from '@angular/http';
import { HttpClientModule, HttpClient } from '@angular/common/http';
import { TranslateModule, TranslateLoader } from '@ngx-translate/core';
import { TranslateHttpLoader } from '@ngx-translate/http-loader';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { SortablejsModule } from 'angular-sortablejs';

import { AlertModule } from 'ngx-bootstrap';

import { AppComponent } from './app.component';
import { IntroComponent } from './intro/intro.component';
import { PlayComponent } from './play/play.component';
import { SettingsComponent } from './settings/settings.component';
import { EditorComponent } from './editor/editor.component';
import { EditorSongComponent } from './editor/editor-song/editor-song.component';
import { EditorSetlistComponent } from './editor/editor-setlist/editor-setlist.component';

import { ApiService } from './services/api.service';
import { StateService } from './services/state.service';
import { TransportService } from './services/transport.service';
import { SongService } from './services/song.service';
import { ConnectionComponent } from './connection/connection.component';

const appRoutes: Routes = [
  { path: 'intro', component: IntroComponent },
  { path: 'play', component: PlayComponent },
  { path: 'editor', component: EditorComponent },
  { path: 'settings', component: SettingsComponent },
  { path: '',
    redirectTo: '/play',
    pathMatch: 'full'
  },
  /*{ path: '**', component: PlayComponent }*/
];

@NgModule({
  declarations: [
    AppComponent,
    IntroComponent,
    PlayComponent,
    SettingsComponent,
    EditorComponent,
    EditorSongComponent,
    EditorSetlistComponent,
    ConnectionComponent
  ],
  imports: [
    HttpModule,
    BrowserModule,
    HttpClientModule,
    RouterModule.forRoot(
      appRoutes,
      { enableTracing: false } // <-- debugging purposes only
    ),
    BrowserAnimationsModule,
    TranslateModule.forRoot({
      loader: {
        provide: TranslateLoader,
        useFactory: HttpLoaderFactory,
        deps: [HttpClient]
      }
    }),
    FormsModule,
    SortablejsModule.forRoot({ 
      animation: 300,
      handle: '.list-sort-handle'
    }),
    AlertModule.forRoot()
  ],
  providers: [
    ApiService,
    StateService,
    TransportService,
    SongService
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }

export function HttpLoaderFactory(http: HttpClient) {
	return new TranslateHttpLoader(http, "./assets/i18n/", ".json");
}